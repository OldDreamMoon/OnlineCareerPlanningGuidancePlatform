import RouteSpinnerScreen from "./RouteSpinnerScreen";

export default function AppLoadingScreen({ title = "正在加载页面..." }: { title?: string }) {
  return <RouteSpinnerScreen label={title} />;
}
