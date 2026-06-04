import { useMemo } from "react";
import { formatMoneyFen } from "../../lib/formatters";

type TrendSeriesItem = {
  key: string;
  label: string;
  paidFen: number;
  refundedFen: number;
};

type MentorFinanceTrendChartProps = {
  trendSeries: TrendSeriesItem[];
};

type TrendChartDatum = {
  key: string;
  label: string;
  paidYuan: number;
  refundedDeltaYuan: number;
};

type TrendChartBarDatum = TrendChartDatum & {
  paidHeightPercent: number;
  refundedHeightPercent: number;
};

type TrendAxisTick = {
  key: string;
  value: number;
  topPercent: number;
  isBaseline: boolean;
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function formatChartMoneyYuan(value: number | string) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return "¥0";
  }

  const formatted = new Intl.NumberFormat("zh-CN", {
    minimumFractionDigits: 0,
    maximumFractionDigits: numeric >= 1000 ? 0 : 1,
  }).format(numeric);

  return `¥${formatted}`;
}

function buildNiceAxisMax(values: number[]) {
  const rawMax = Math.max(1, ...values.map((value) => Math.abs(value)));
  const magnitude = 10 ** Math.floor(Math.log10(rawMax));
  const normalized = rawMax / magnitude;

  if (normalized <= 1) {
    return magnitude;
  }
  if (normalized <= 2) {
    return 2 * magnitude;
  }
  if (normalized <= 5) {
    return 5 * magnitude;
  }
  return 10 * magnitude;
}

function buildAxisTicks(axisMax: number): TrendAxisTick[] {
  const segments = 4;
  return Array.from({ length: segments + 1 }, (_, index) => {
    const ratio = index / segments;
    const value = axisMax - axisMax * 2 * ratio;
    return {
      key: `${index}-${value}`,
      value,
      topPercent: ratio * 100,
      isBaseline: Math.abs(value) < 0.000001,
    };
  });
}

function shouldShowTrendLabel(index: number, total: number) {
  if (total <= 8) {
    return true;
  }

  const step = Math.max(1, Math.ceil((total - 1) / 7));
  return index === 0 || index === total - 1 || index % step === 0;
}

export default function MentorFinanceTrendChart({ trendSeries }: MentorFinanceTrendChartProps) {
  const chartData = useMemo<TrendChartDatum[]>(
    () => trendSeries.map((item) => ({
      key: item.key,
      label: item.label,
      paidYuan: item.paidFen / 100,
      refundedDeltaYuan: -(item.refundedFen / 100),
    })),
    [trendSeries],
  );

  const displayChartData = useMemo(
    () => chartData.filter((item) => item.paidYuan > 0 || item.refundedDeltaYuan < 0),
    [chartData],
  );

  const trendHasActivity = useMemo(
    () => trendSeries.some((item) => item.paidFen > 0 || item.refundedFen > 0),
    [trendSeries],
  );

  const trendSummaryItems = useMemo(
    () => [
      {
        label: "成交",
        value: formatMoneyFen(trendSeries.reduce((sum, item) => sum + item.paidFen, 0)),
        dotClassName: "bg-indigo-500",
        toneClassName: "border-indigo-100 bg-indigo-50/70 text-indigo-700",
      },
      {
        label: "退款",
        value: formatMoneyFen(trendSeries.reduce((sum, item) => sum + item.refundedFen, 0)),
        dotClassName: "bg-rose-400",
        toneClassName: "border-rose-100 bg-rose-50/70 text-rose-700",
      },
    ],
    [trendSeries],
  );

  const axisMax = useMemo(
    () => buildNiceAxisMax(displayChartData.flatMap((item) => [item.paidYuan, item.refundedDeltaYuan])),
    [displayChartData],
  );

  const axisTicks = useMemo(() => buildAxisTicks(axisMax), [axisMax]);

  const chartBars = useMemo<TrendChartBarDatum[]>(
    () => displayChartData.map((item) => ({
      ...item,
      paidHeightPercent: axisMax > 0 ? (item.paidYuan / axisMax) * 50 : 0,
      refundedHeightPercent: axisMax > 0 ? (Math.abs(item.refundedDeltaYuan) / axisMax) * 50 : 0,
    })),
    [axisMax, displayChartData],
  );

  return (
    <>
      <div className="mt-5 flex flex-wrap items-center gap-3">
        {trendSummaryItems.map((item) => (
          <div
            key={item.label}
            className={joinClasses(
              "inline-flex items-center gap-3 rounded-full border px-4 py-2 text-[13px] font-semibold",
              item.toneClassName,
            )}
          >
            <span className={joinClasses("h-2.5 w-2.5 rounded-full", item.dotClassName)} />
            <span>{item.label}</span>
            <span>{item.value}</span>
          </div>
        ))}
      </div>

      <div className="mt-5 rounded-[1.5rem] border border-slate-100 bg-slate-50/70 px-3 py-4">
        {trendHasActivity ? (
          <div className="grid min-h-[250px] grid-cols-[52px_minmax(0,1fr)] gap-3">
            <div className="relative h-[208px]">
              {axisTicks.map((tick) => (
                <div
                  key={tick.key}
                  className={joinClasses(
                    "absolute right-0 -translate-y-1/2 text-right text-[11px] font-medium",
                    tick.isBaseline ? "text-slate-500" : "text-slate-400",
                  )}
                  style={{ top: `${tick.topPercent}%` }}
                >
                  {formatChartMoneyYuan(tick.value)}
                </div>
              ))}
            </div>

            <div className="min-w-0">
              <div className="relative h-[208px] overflow-visible rounded-[1.25rem] border border-slate-100 bg-white/72 px-2 shadow-[inset_0_1px_0_rgba(255,255,255,0.6)]">
                {axisTicks.map((tick) => (
                  <div
                    key={tick.key}
                    className={joinClasses(
                      "pointer-events-none absolute inset-x-2 -translate-y-1/2 border-t",
                      tick.isBaseline
                        ? "border-slate-300"
                        : "border-dashed border-slate-200",
                    )}
                    style={{ top: `${tick.topPercent}%` }}
                  />
                ))}

                <div className="absolute inset-0 flex items-stretch gap-2 px-2 py-1">
                  {chartBars.map((item) => (
                    <div
                      key={item.key}
                      role="img"
                      tabIndex={0}
                      aria-label={`${item.label}，成交 ${formatChartMoneyYuan(item.paidYuan)}，退款 ${formatChartMoneyYuan(Math.abs(item.refundedDeltaYuan))}`}
                      className="group relative flex min-w-0 flex-1 items-stretch justify-center rounded-[1rem] border border-transparent bg-transparent px-0.5 transition-colors hover:border-slate-200 hover:bg-slate-50/90 focus-visible:border-slate-300 focus-visible:bg-slate-50/90 focus-visible:outline-none"
                    >
                      <div className="pointer-events-none absolute left-1/2 top-2 z-20 hidden w-max max-w-[15rem] -translate-x-1/2 rounded-2xl border border-slate-200 bg-white/96 px-4 py-3 text-left shadow-[0_16px_40px_rgba(15,23,42,0.12)] backdrop-blur group-hover:block group-focus-visible:block">
                        <div className="text-[13px] font-semibold text-slate-900">{item.label}</div>
                        <div className="mt-2 space-y-2">
                          <div className="flex items-center justify-between gap-4 text-[13px]">
                            <div className="flex items-center gap-2 text-slate-600">
                              <span className="h-2.5 w-2.5 rounded-full bg-indigo-500" />
                              <span>成交</span>
                            </div>
                            <span className="font-semibold text-slate-900">{formatChartMoneyYuan(item.paidYuan)}</span>
                          </div>
                          <div className="flex items-center justify-between gap-4 text-[13px]">
                            <div className="flex items-center gap-2 text-slate-600">
                              <span className="h-2.5 w-2.5 rounded-full bg-rose-400" />
                              <span>退款</span>
                            </div>
                            <span className="font-semibold text-slate-900">
                              {formatChartMoneyYuan(Math.abs(item.refundedDeltaYuan))}
                            </span>
                          </div>
                        </div>
                      </div>

                      <span
                        className="absolute left-1/2 bottom-1/2 w-[12px] -translate-x-1/2 rounded-t-[8px] bg-indigo-500 shadow-[0_10px_24px_rgba(99,102,241,0.24)] transition-opacity group-hover:opacity-90"
                        style={{ height: `${item.paidHeightPercent}%` }}
                      />
                      <span
                        className="absolute left-1/2 top-1/2 w-[12px] -translate-x-1/2 rounded-b-[8px] bg-rose-400 shadow-[0_10px_24px_rgba(251,113,133,0.2)] transition-opacity group-hover:opacity-90"
                        style={{ height: `${item.refundedHeightPercent}%` }}
                      />
                    </div>
                  ))}
                </div>
              </div>

              <div className="mt-3 flex min-w-0 gap-2 px-2">
                {chartBars.map((item, index) => {
                  const visible = shouldShowTrendLabel(index, chartBars.length);
                  return (
                    <div key={item.key} className="flex min-w-0 flex-1 justify-center">
                      <span
                        title={item.label}
                        className={joinClasses(
                          "block truncate text-center text-[11px] font-medium leading-4 text-slate-500",
                          !visible && "opacity-0",
                        )}
                      >
                        {item.label}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        ) : (
          <div className="flex h-[250px] items-center justify-center rounded-[1.25rem] border border-dashed border-slate-200 bg-white/80 px-6 text-center text-[15px] leading-7 text-slate-500">
            当前范围内还没有成交或退款记录，切换时间范围后再查看。
          </div>
        )}
      </div>
    </>
  );
}
