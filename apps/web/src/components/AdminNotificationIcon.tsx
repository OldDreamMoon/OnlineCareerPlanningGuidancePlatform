import { Bell } from "lucide-react";

type AdminNotificationIconProps = {
  hasNotifications?: boolean;
};

export default function AdminNotificationIcon({ hasNotifications = false }: AdminNotificationIconProps) {
  return (
    <div
      className={`flex h-8 w-8 cursor-pointer items-center justify-center rounded-full transition-colors ${hasNotifications ? "bg-orange-50 hover:bg-orange-100" : "bg-gray-100 hover:bg-gray-200"}`}
    >
      <Bell size={18} className={hasNotifications ? "text-orange-600" : "text-gray-700"} />
    </div>
  );
}
