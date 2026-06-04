import {
  createContext,
  lazy,
  Suspense,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import {
  createPath,
  useLocation,
  useNavigate,
  type NavigateOptions,
  type To,
} from "react-router-dom";

const SkillsPageLoader = lazy(async () => {
  const module = await import("./SkillsPageLoader");
  return { default: module.SkillsPageLoader };
});

type SkillsRouteTransitionContextValue = {
  isSkillsRouteTransitionRunning: boolean;
  navigateWithSkillsTransition: (to: To, options?: NavigateOptions) => void;
  notifySkillsPageLoadingState: (loading: boolean) => void;
};

type PendingNavigation = {
  to: string;
  options?: NavigateOptions;
  waitForSkillsPageReady: boolean;
  committed: boolean;
};

type TransitionMode = "idle" | "enter" | "leave" | "direct";

const SkillsRouteTransitionContext = createContext<SkillsRouteTransitionContextValue | null>(null);

function isSkillsPath(pathname: string) {
  return pathname === "/skills";
}

function buildInternalHref(to: To) {
  const raw = typeof to === "string" ? to : createPath(to);
  const url = new URL(raw, window.location.href);
  return `${url.pathname}${url.search}${url.hash}`;
}

export function SkillsRouteTransitionProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [loaderVisible, setLoaderVisible] = useState(false);
  const [loaderActive, setLoaderActive] = useState(false);
  const [loaderShowLoading, setLoaderShowLoading] = useState(false);
  const [loaderKey, setLoaderKey] = useState(0);
  const transitionModeRef = useRef<TransitionMode>("idle");
  const pendingNavigationRef = useRef<PendingNavigation | null>(null);

  const closeLoader = useCallback(() => {
    transitionModeRef.current = "idle";
    pendingNavigationRef.current = null;
    setLoaderVisible(false);
    setLoaderActive(false);
    setLoaderShowLoading(false);
  }, []);

  const startLoader = useCallback((mode: TransitionMode, pendingNavigation?: PendingNavigation | null) => {
    if (transitionModeRef.current !== "idle") {
      return false;
    }

    transitionModeRef.current = mode;
    pendingNavigationRef.current = pendingNavigation ?? null;
    setLoaderKey((current) => current + 1);
    setLoaderVisible(true);
    setLoaderActive(true);
    setLoaderShowLoading(false);
    return true;
  }, []);

  const navigateWithSkillsTransition = useCallback((to: To, options?: NavigateOptions) => {
    const currentIsSkills = isSkillsPath(location.pathname);
    const nextHref = buildInternalHref(to);
    const nextUrl = new URL(nextHref, window.location.origin);
    const nextIsSkills = isSkillsPath(nextUrl.pathname);

    if (!currentIsSkills && !nextIsSkills) {
      navigate(to, options);
      return;
    }

    startLoader(nextIsSkills ? "enter" : "leave", {
      to: `${nextUrl.pathname}${nextUrl.search}${nextUrl.hash}`,
      options,
      waitForSkillsPageReady: nextIsSkills,
      committed: false,
    });
  }, [location.pathname, navigate, startLoader]);

  const notifySkillsPageLoadingState = useCallback((loading: boolean) => {
    if (!isSkillsPath(location.pathname)) {
      return;
    }

    const currentMode = transitionModeRef.current;

    if (loading) {
      if (currentMode === "idle") {
        startLoader("direct");
      }
      return;
    }

    if (currentMode === "enter" || currentMode === "direct") {
      setLoaderActive(false);
    }
  }, [location.pathname, startLoader]);

  useEffect(() => {
    const handleDocumentClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }

      const eventTarget = event.target;
      if (!(eventTarget instanceof Element)) {
        return;
      }

      const anchor = eventTarget.closest("a[href]");
      if (!(anchor instanceof HTMLAnchorElement)) {
        return;
      }

      if ((anchor.target && anchor.target !== "_self") || anchor.hasAttribute("download")) {
        return;
      }

      const hrefAttribute = anchor.getAttribute("href");
      if (!hrefAttribute || hrefAttribute.startsWith("mailto:") || hrefAttribute.startsWith("tel:")) {
        return;
      }

      const targetUrl = new URL(anchor.href, window.location.href);
      if (targetUrl.origin !== window.location.origin) {
        return;
      }

      const currentIsSkills = isSkillsPath(location.pathname);
      const nextIsSkills = isSkillsPath(targetUrl.pathname);
      if (!currentIsSkills && !nextIsSkills) {
        return;
      }

      if (
        targetUrl.pathname === location.pathname &&
        targetUrl.search === location.search &&
        targetUrl.hash !== "" &&
        targetUrl.hash !== location.hash
      ) {
        return;
      }

      event.preventDefault();
      event.stopPropagation();

      startLoader(nextIsSkills ? "enter" : "leave", {
        to: `${targetUrl.pathname}${targetUrl.search}${targetUrl.hash}`,
        waitForSkillsPageReady: nextIsSkills,
        committed: false,
      });
    };

    document.addEventListener("click", handleDocumentClick, true);
    return () => {
      document.removeEventListener("click", handleDocumentClick, true);
    };
  }, [location.hash, location.pathname, location.search, startLoader]);

  useEffect(() => {
    const pendingNavigation = pendingNavigationRef.current;
    if (!pendingNavigation?.committed) {
      return;
    }

    const currentHref = `${location.pathname}${location.search}${location.hash}`;
    if (currentHref !== pendingNavigation.to) {
      return;
    }

    setLoaderShowLoading(true);

    if (!pendingNavigation.waitForSkillsPageReady) {
      setLoaderActive(false);
    }
  }, [location.hash, location.pathname, location.search]);

  const handleLoaderCovered = useCallback(() => {
    const pendingNavigation = pendingNavigationRef.current;
    if (!pendingNavigation) {
      setLoaderShowLoading(true);
      return;
    }

    if (pendingNavigation.committed) {
      return;
    }

    pendingNavigation.committed = true;
    navigate(pendingNavigation.to, pendingNavigation.options);
  }, [navigate]);

  const contextValue = useMemo<SkillsRouteTransitionContextValue>(() => ({
    isSkillsRouteTransitionRunning: loaderVisible,
    navigateWithSkillsTransition,
    notifySkillsPageLoadingState,
  }), [loaderVisible, navigateWithSkillsTransition, notifySkillsPageLoadingState]);

  return (
    <SkillsRouteTransitionContext.Provider value={contextValue}>
      {children}
      {loaderVisible ? (
        <Suspense fallback={null}>
          <SkillsPageLoader
            key={loaderKey}
            active={loaderActive}
            showLoading={loaderShowLoading}
            onCovered={handleLoaderCovered}
            onExited={closeLoader}
          />
        </Suspense>
      ) : null}
    </SkillsRouteTransitionContext.Provider>
  );
}

export function useSkillsRouteTransition() {
  const context = useContext(SkillsRouteTransitionContext);

  if (!context) {
    throw new Error("useSkillsRouteTransition must be used within SkillsRouteTransitionProvider");
  }

  return context;
}
