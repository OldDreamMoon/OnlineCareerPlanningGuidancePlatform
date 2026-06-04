import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import RouteSpinnerScreen from "../components/RouteSpinnerScreen";
import { getPendingRegistrationSnapshot } from "../lib/pendingRegistration";
import { resolveWorkspaceDashboardRoute } from "../lib/workspaceRoutes";

function resolvePostAuthDestination(role: string | null) {
  return resolveWorkspaceDashboardRoute(role);
}

export default function AuthStepTwoPage() {
  const { ready, isAuthenticated, role } = useAuth();
  const location = useLocation();
  const pendingRegistration = getPendingRegistrationSnapshot();
  const searchParams = new URLSearchParams(location.search);

  if (!ready) {
    return <RouteSpinnerScreen label="正在切换到新的认证流程" />;
  }

  if (isAuthenticated) {
    return <Navigate to={resolvePostAuthDestination(role)} replace />;
  }

  searchParams.set("mode", "register");

  if (pendingRegistration) {
    searchParams.set("panel", "register-details");
    if (pendingRegistration.preferredRole) {
      searchParams.set("role", pendingRegistration.preferredRole);
    }
  } else {
    searchParams.delete("panel");
  }

  return <Navigate to={`/auth?${searchParams.toString()}`} replace state={location.state} />;
}
