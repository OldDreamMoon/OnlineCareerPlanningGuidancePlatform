import { Navigate, useLocation, useParams } from "react-router-dom";

export default function MentorDetailRedirectPage() {
  const { mentorUserId } = useParams();
  const location = useLocation();

  const nextSearchParams = new URLSearchParams(location.search);
  if (mentorUserId) {
    nextSearchParams.set("mentor", mentorUserId);
  }

  const nextSearch = nextSearchParams.toString();
  return <Navigate to={`/mentors${nextSearch ? `?${nextSearch}` : ""}`} replace />;
}
