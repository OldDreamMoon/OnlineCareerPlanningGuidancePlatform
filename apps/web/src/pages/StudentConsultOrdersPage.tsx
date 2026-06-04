import {
  AlertCircle,
  ArrowRight,
  CalendarDays,
  Clock3,
  CreditCard,
  Loader2,
  RefreshCw,
  Sparkles,
  Target,
} from "lucide-react";
import { startTransition, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import StudentWorkspaceNav from "../components/student/StudentWorkspaceNav";
import { ApiClientError, apiRequest, buildQuery } from "../lib/apiClient";
import { formatDateTime, formatMoneyFen } from "../lib/formatters";

type ConsultOrderListResponse = {
  records: ConsultOrderItem[];
  total: number;
  page: number;
  size: number;
};

type ConsultOrderItem = {
  orderNo: string;
  counterpartUserId: number;
  counterpartDisplayName: string;
  amountFen: number;
  status: string;
  questionText: string | null;
  paymentMode: string | null;
  appointmentStartAt: string | null;
  appointmentEndAt: string | null;
  createdAt: string | null;
  paidAt: string | null;
  closedAt: string | null;
  autoCancelAt: string | null;
};

type StatusFilter =
  | "ALL"
  | "CREATED"
  | "PAYING"
  | "PAID"
  | "ANSWERED"
  | "CLOSED"
  | "REFUNDED"
  | "CANCELED";

const PAGE_SIZE = 8;

const STATUS_META: Record<string, { label: string; className: string }> = {
  CREATED: { label: "待支付", className: "border-slate-200 bg-white text-slate-700" },
  PAYING: { label: "支付中", className: "border-cyan-200 bg-cyan-50 text-cyan-700" },
  PAID: { label: "待导师回复", className: "border-amber-200 bg-amber-50 text-amber-700" },
  ANSWERED: { label: "已回复待确认", className: "border-blue-200 bg-blue-50 text-blue-700" },
  CLOSED: { label: "已完成", className: "border-emerald-200 bg-emerald-50 text-emerald-700" },
  REFUNDED: { label: "已退款", className: "border-slate-200 bg-slate-100 text-slate-600" },
  CANCELED: { label: "已取消", className: "border-slate-200 bg-slate-100 text-slate-600" },
  FAILED: { label: "支付失败", className: "border-rose-200 bg-rose-50 text-rose-700" },
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function buildErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError && error.message) {
    return error.traceId ? `${error.message}（traceId: ${error.traceId}）` : error.message;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function getPageItems(currentPage: number, totalPages: number) {
  if (totalPages <= 5) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }

  const pages = new Set<number>([1, totalPages, currentPage]);
  if (currentPage - 1 > 1) {
    pages.add(currentPage - 1);
  }
  if (currentPage + 1 < totalPages) {
    pages.add(currentPage + 1);
  }

  const sortedPages = Array.from(pages).sort((left, right) => left - right);
  const items: Array<number | string> = [];

  sortedPages.forEach((page, index) => {
    items.push(page);
    const nextPage = sortedPages[index + 1];
    if (nextPage && nextPage - page > 1) {
      items.push("...");
    }
  });

  return items;
}

function isAppointmentOrder(order: Pick<ConsultOrderItem, "appointmentStartAt" | "appointmentEndAt">) {
  return Boolean(order.appointmentStartAt || order.appointmentEndAt);
}

function getPrimaryActionLabel(order: ConsultOrderItem) {
  switch (order.status) {
    case "CREATED":
    case "PAYING":
      return "继续支付";
    case "PAID":
      return "查看等待状态";
    case "ANSWERED":
      return "查看导师答复";
    case "CLOSED":
      return "查看已完成订单";
    case "REFUNDED":
      return "查看退款结果";
    case "CANCELED":
      return "查看取消记录";
    default:
      return "查看详情";
  }
}

export default function StudentConsultOrdersPage() {
  const { role, displayName } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const [statusFilter, setStatusFilter] = useState<StatusFilter>(() => {
    const rawValue = searchParams.get("status");
    switch (rawValue) {
      case "CREATED":
      case "PAYING":
      case "PAID":
      case "ANSWERED":
      case "CLOSED":
      case "REFUNDED":
      case "CANCELED":
        return rawValue;
      default:
        return "ALL";
    }
  });
  const [page, setPage] = useState(Math.max(1, Number(searchParams.get("page") || "1")));
  const [ordersData, setOrdersData] = useState<ConsultOrderListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [refreshSeed, setRefreshSeed] = useState(0);

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil((ordersData?.total ?? 0) / PAGE_SIZE)),
    [ordersData?.total],
  );
  const pageItems = useMemo(() => getPageItems(page, totalPages), [page, totalPages]);

  useEffect(() => {
    // 订单列表筛选同步到 URL，通知或刷新后仍能停在同一状态页。
    const nextParams = new URLSearchParams();
    if (statusFilter !== "ALL") {
      nextParams.set("status", statusFilter);
    }
    if (page > 1) {
      nextParams.set("page", String(page));
    }
    startTransition(() => {
      setSearchParams(nextParams, { replace: true });
    });
  }, [page, setSearchParams, statusFilter]);

  useEffect(() => {
    if (role !== "STUDENT") {
      return;
    }

    let active = true;
    setLoading(true);

    // 列表接口会懒刷新未支付超时和导师超时状态，前端只展示返回结果。
    void apiRequest<ConsultOrderListResponse>(`/consult/orders${buildQuery({
      page,
      size: PAGE_SIZE,
      status: statusFilter === "ALL" ? undefined : statusFilter,
    })}`)
      .then((response) => {
        if (!active) {
          return;
        }
        setOrdersData(response);
        setErrorMessage(null);
      })
      .catch((error) => {
        if (!active) {
          return;
        }
        setOrdersData(null);
        setErrorMessage(buildErrorMessage(error, "加载咨询订单失败"));
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [page, refreshSeed, role, statusFilter]);

  return (
    <div className="min-h-screen bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.14),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_28%),linear-gradient(180deg,#eff6ff_0%,#f8fafc_58%,#f8fafc_100%)]" />
      <StudentWorkspaceNav
        displayName={displayName}
        activeKey="consultOrders"
        sectionLabel="Consult Orders"
        title="我的咨询订单"
        extraAction={(
          <Link
            to="/mentors"
            className="hidden rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 md:inline-flex"
          >
            再找导师
          </Link>
        )}
      />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 py-8 sm:px-6 lg:px-8">
        <section className="rounded-[2rem] border border-white/80 bg-white/74 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.12)] backdrop-blur-xl">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-3xl">
              <h1 className="text-3xl font-black tracking-tight text-slate-900 lg:text-[2.55rem]">我的咨询订单</h1>
              <p className="mt-3 text-sm leading-7 text-slate-600 lg:text-[15px]">
                在这里查看已创建的咨询单，并继续处理支付、材料、回复、评价和售后。
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4">
                <div className="text-sm font-bold text-slate-500">当前总量</div>
                <div className="mt-2 text-2xl font-black text-slate-900">{ordersData?.total ?? "—"}</div>
              </div>
              <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4">
                <div className="text-sm font-bold text-slate-500">筛选状态</div>
                <div className="mt-2 text-sm font-semibold text-slate-900">{statusFilter === "ALL" ? "全部订单" : STATUS_META[statusFilter]?.label || statusFilter}</div>
              </div>
            </div>
          </div>

          <div className="mt-6 flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div className="flex flex-wrap gap-2">
              {([
                { value: "ALL", label: "全部订单" },
                { value: "CREATED", label: "待支付" },
                { value: "PAID", label: "待回复" },
                { value: "ANSWERED", label: "待确认" },
                { value: "CLOSED", label: "已完成" },
                { value: "REFUNDED", label: "已退款" },
              ] as Array<{ value: StatusFilter; label: string }>).map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => {
                    setStatusFilter(option.value);
                    setPage(1);
                  }}
                  className={joinClasses(
                    "rounded-full border px-4 py-2 text-sm font-semibold transition-colors",
                    statusFilter === option.value
                      ? "border-indigo-500 bg-indigo-600 text-white"
                      : "border-slate-200 bg-white text-slate-600 hover:border-indigo-200 hover:text-indigo-600",
                  )}
                >
                  {option.label}
                </button>
              ))}
            </div>

            <button
              type="button"
              onClick={() => setRefreshSeed((current) => current + 1)}
              className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:text-slate-900"
            >
              <RefreshCw size={16} className="mr-2" />
              刷新订单
            </button>
          </div>
        </section>

        {errorMessage ? (
          <section className="mt-6 rounded-[1.8rem] border border-rose-200 bg-rose-50 px-5 py-4 text-sm text-rose-700">
            <div className="flex items-start gap-3">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <div>
                <div className="font-semibold">订单列表加载失败</div>
                <div className="mt-1">{errorMessage}</div>
              </div>
            </div>
          </section>
        ) : null}

        <section className="mt-6">
          {loading ? (
            <div className="grid gap-6 lg:grid-cols-2">
              {Array.from({ length: 6 }).map((_, index) => (
                <div key={index} className="h-[250px] rounded-[1.9rem] border border-white/80 bg-white/80 shadow-[0_20px_50px_rgba(148,163,184,0.10)]" />
              ))}
            </div>
          ) : ordersData?.records.length ? (
            <div className="grid gap-6 lg:grid-cols-2">
              {ordersData.records.map((order) => {
                const statusMeta = STATUS_META[order.status] ?? { label: order.status, className: "border-slate-200 bg-slate-100 text-slate-600" };
                return (
                  <article
                    key={order.orderNo}
                    className="flex h-full flex-col rounded-[1.9rem] border border-white/80 bg-white/84 p-5 shadow-[0_20px_50px_rgba(148,163,184,0.10)] backdrop-blur-sm"
                  >
                    <div className="flex items-center justify-between gap-4">
                      <div>
                        <div className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm font-semibold text-slate-600">
                          <Target size={13} />
                          {order.counterpartDisplayName}
                        </div>
                        <h2 className="mt-3 text-xl font-black text-slate-950">{order.questionText?.trim() || "当前订单还没有额外摘要"}</h2>
                      </div>
                      <span className={joinClasses("shrink-0 rounded-full border px-3 py-1 text-xs font-semibold", statusMeta.className)}>
                        {statusMeta.label}
                      </span>
                    </div>

                    <div className="mt-5 flex flex-wrap gap-2">
                      <span className="rounded-full border border-indigo-100 bg-indigo-50 px-3 py-1.5 text-xs font-semibold text-indigo-700">
                        金额：{formatMoneyFen(order.amountFen)}
                      </span>
                      <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-semibold text-slate-600">
                        {isAppointmentOrder(order) ? "预约咨询" : "异步答疑"}
                      </span>
                      {order.paymentMode ? (
                        <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-semibold text-slate-600">
                          支付模式：{order.paymentMode}
                        </span>
                      ) : null}
                    </div>

                    <div className="mt-5 grid gap-3 rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4 text-sm text-slate-600">
                      <div className="flex items-center justify-between gap-3">
                        <span className="inline-flex items-center gap-2">
                          <CalendarDays size={15} className="text-slate-400" />
                          创建时间
                        </span>
                        <span className="font-semibold text-slate-900">{order.createdAt ? formatDateTime(order.createdAt) : "—"}</span>
                      </div>
                      <div className="flex items-center justify-between gap-3">
                        <span className="inline-flex items-center gap-2">
                          <CreditCard size={15} className="text-slate-400" />
                          支付 / 完成
                        </span>
                        <span className="font-semibold text-slate-900">
                          {order.paidAt ? formatDateTime(order.paidAt) : order.closedAt ? formatDateTime(order.closedAt) : "等待中"}
                        </span>
                      </div>
                      <div className="flex items-center justify-between gap-3">
                        <span className="inline-flex items-center gap-2">
                          <Clock3 size={15} className="text-slate-400" />
                          预约 / 自动取消
                        </span>
                        <span className="font-semibold text-slate-900">
                          {order.appointmentStartAt
                            ? `${formatDateTime(order.appointmentStartAt)} - ${formatDateTime(order.appointmentEndAt)}`
                            : order.autoCancelAt
                              ? `自动取消：${formatDateTime(order.autoCancelAt)}`
                              : "未设置"}
                        </span>
                      </div>
                    </div>

                    {order.autoCancelAt && (order.status === "CREATED" || order.status === "PAYING") ? (
                      <div className="mt-4 rounded-[1.4rem] border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800">
                        当前订单仍未支付，系统会在 {formatDateTime(order.autoCancelAt)} 自动取消。建议尽快进入详情页继续处理。
                      </div>
                    ) : null}

                    <div className="mt-5 flex items-center justify-end">
                      <Link
                        to={`/consult/orders/${order.orderNo}`}
                        className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:border-indigo-200 hover:text-indigo-600"
                      >
                        {getPrimaryActionLabel(order)}
                        <ArrowRight size={15} className="ml-2" />
                      </Link>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <div className="rounded-[2rem] border border-dashed border-slate-200 bg-white/72 px-6 py-10 text-center shadow-[0_18px_45px_rgba(148,163,184,0.08)]">
              <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-indigo-50 text-indigo-400">
                <Sparkles size={28} />
              </div>
              <h2 className="mt-4 text-xl font-bold text-slate-900">当前筛选条件下还没有订单</h2>
              <p className="mt-2 text-sm leading-7 text-slate-500">
                可以先回导师广场继续准备问题与材料。
              </p>
              <Link
                to="/mentors"
                className="mt-5 inline-flex items-center rounded-full border border-indigo-200 bg-indigo-50 px-4 py-2 text-sm font-semibold text-indigo-700 transition-colors hover:bg-indigo-100"
              >
                去导师广场看看
              </Link>
            </div>
          )}
        </section>

        {!loading && ordersData?.records.length ? (
          <section className="mt-8 flex items-center justify-center gap-2">
            <button
              type="button"
              onClick={() => setPage((current) => Math.max(current - 1, 1))}
              disabled={page <= 1}
              className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {loading ? <Loader2 size={16} className="animate-spin" /> : "‹"}
            </button>
            {pageItems.map((item, index) => (
              <button
                key={`${item}-${index}`}
                type="button"
                disabled={item === "..."}
                onClick={() => {
                  if (typeof item === "number") {
                    setPage(item);
                  }
                }}
                className={joinClasses(
                  "inline-flex h-10 w-10 items-center justify-center rounded-full text-sm font-bold transition-colors",
                  item === page
                    ? "bg-indigo-600 text-white shadow-md"
                    : item === "..."
                      ? "cursor-default text-slate-400"
                      : "border border-slate-200 bg-white text-slate-600 shadow-sm hover:border-indigo-200 hover:text-indigo-600",
                )}
              >
                {item}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setPage((current) => Math.min(current + 1, totalPages))}
              disabled={page >= totalPages}
              className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-40"
            >
              ›
            </button>
          </section>
        ) : null}
      </main>
    </div>
  );
}
