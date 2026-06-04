import { BellRing, ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import {
  getNotificationActionLabel,
  resolveNotificationHref,
  type NotificationRecord,
} from "../../lib/notifications";
import { formatDateTime } from "../../lib/formatters";
import { type SessionRole } from "../../lib/sessionStore";

type CommunityNotificationEntryProps = {
  notification: NotificationRecord;
  role: string | null;
};

export default function CommunityNotificationEntry({
  notification,
  role,
}: CommunityNotificationEntryProps) {
  return (
    <Link
      to={resolveNotificationHref(notification, role as SessionRole)}
      className="group relative flex gap-3 rounded-[1.3rem] border border-slate-100 bg-slate-50 px-4 py-4 transition-colors hover:border-indigo-100 hover:bg-indigo-50/40"
    >
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-indigo-50 text-indigo-600">
        <BellRing size={16} />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-3">
          <div className="truncate text-base font-bold text-slate-900">{notification.title}</div>
          {!notification.read ? <span className="h-2 w-2 shrink-0 rounded-full bg-orange-500" /> : null}
        </div>
        <div className="mt-1.5 line-clamp-2 text-sm leading-7 text-slate-500">{notification.content}</div>
        <div className="mt-2 flex items-center justify-between gap-3">
          <span className="text-sm font-semibold text-indigo-600">
            {getNotificationActionLabel(notification, role as SessionRole)}
          </span>
          <span className="inline-flex items-center gap-1 text-sm text-slate-400">
            {formatDateTime(notification.createdAt)}
            <ChevronRight size={12} className="transition-transform group-hover:translate-x-0.5" />
          </span>
        </div>
      </div>
    </Link>
  );
}
