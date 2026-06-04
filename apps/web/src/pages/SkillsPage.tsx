import { lazy, Suspense } from "react";
import RouteSpinnerScreen from "../components/RouteSpinnerScreen";

const SkillsPageContent = lazy(() => import("./SkillsPageContent"));

export default function SkillsPage() {
  return (
    <Suspense fallback={<RouteSpinnerScreen label="正在加载技能星图" />}>
      <SkillsPageContent />
    </Suspense>
  );
}
