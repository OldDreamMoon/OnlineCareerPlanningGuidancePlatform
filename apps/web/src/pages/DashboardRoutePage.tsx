import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import RouteSpinnerScreen from "../components/RouteSpinnerScreen";
import { resolveWorkspaceDashboardRoute } from "../lib/workspaceRoutes";

export default function DashboardRoutePage() {
  const { ready, role } = useAuth();

  if (!ready) {
    return <RouteSpinnerScreen label="正在定位工作台" />;
  }

  return <Navigate to={resolveWorkspaceDashboardRoute(role)} replace />;
}
