import RouteSpinnerScreen from "./RouteSpinnerScreen";

type WorkspacePageLoadingScreenProps = {
  title: string;
  description: string;
};

export default function WorkspacePageLoadingScreen({
  title,
}: WorkspacePageLoadingScreenProps) {
  return <RouteSpinnerScreen label={title} />;
}
