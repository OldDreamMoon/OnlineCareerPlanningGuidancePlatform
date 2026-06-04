import { type ReactNode } from "react";
import StudentWorkspaceTopbar, {
  buildStudentWorkspacePrimaryNav,
  type StudentWorkspaceTopbarPrimarySection,
} from "./StudentWorkspaceTopbar";

type StudentWorkspaceNavKey = "dashboard" | "mentors" | "consultOrders" | "bounty";

type StudentWorkspaceNavProps = {
  displayName: string | null;
  activeKey: StudentWorkspaceNavKey;
  sectionLabel?: string;
  title?: string;
  extraAction?: ReactNode;
};

const ACTIVE_SECTION_MAP: Record<StudentWorkspaceNavKey, StudentWorkspaceTopbarPrimarySection> = {
  dashboard: "dashboard",
  mentors: "mentors",
  consultOrders: "consultOrders",
  bounty: "enterprisePractice",
};

export default function StudentWorkspaceNav({
  displayName,
  activeKey,
  sectionLabel,
  title,
  extraAction,
}: StudentWorkspaceNavProps) {
  return (
    <StudentWorkspaceTopbar
      sectionLabel={sectionLabel}
      title={title}
      displayName={displayName}
      navItems={buildStudentWorkspacePrimaryNav(ACTIVE_SECTION_MAP[activeKey])}
      rightActions={extraAction}
      position="sticky"
    />
  );
}
