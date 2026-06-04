import { Navigate, useLocation } from "react-router-dom";

export default function RegisterPage() {
  const location = useLocation();
  const searchParams = new URLSearchParams(location.search);

  searchParams.set("mode", "register");

  return <Navigate to={`/auth?${searchParams.toString()}`} replace state={location.state} />;
}
