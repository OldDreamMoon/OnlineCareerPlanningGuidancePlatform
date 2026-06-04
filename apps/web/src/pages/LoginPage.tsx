import { Navigate, useLocation } from "react-router-dom";

export default function LoginPage() {
  const location = useLocation();
  const searchParams = new URLSearchParams(location.search);

  searchParams.set("mode", "login");

  return <Navigate to={`/auth?${searchParams.toString()}`} replace state={location.state} />;
}
