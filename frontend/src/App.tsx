import { startTransition, useEffect, useState } from "react";
import { authGateway } from "./app/auth";
import { AuthPage } from "./pages/auth/AuthPage";
import { BrowsePage } from "./pages/BrowsePage";
import { LandingPage } from "./pages/LandingPage";
import { RequestPage } from "./pages/RequestPage";
import { PublicFooter } from "./components/layout/PublicFooter";
import { PublicHeader } from "./components/layout/PublicHeader";
import { CustomerShell } from "./components/layout/CustomerShell";
import { FsShell } from "./components/layout/FsShell";
import { MyStoragePage } from "./pages/MyStoragePage";
import { DailySchedule } from "./pages/fs/DailySchedule";
import { OnsiteHandoverChecklist } from "./pages/fs/OnsiteHandoverChecklist";
import { OnsiteReturnChecklist } from "./pages/fs/OnsiteReturnChecklist";
import { SupportRequestHandling } from "./pages/fs/SupportRequestHandling";
import { IncidentReportForm } from "./pages/fs/IncidentReportForm";
import type { Theme } from "./components/ui/ThemeToggle";
import { viewFromLocation } from "./app/routes";
import { View } from "./app/types";

const THEME_STORAGE_KEY = "kho-moc-theme";

function getInitialTheme(): Theme {
  try {
    return window.localStorage.getItem(THEME_STORAGE_KEY) === "light" ? "light" : "dark";
  } catch {
    return "dark";
  }
}

function App() {
  const [view, setView] = useState<View>(viewFromLocation);
  const [theme, setTheme] = useState<Theme>(getInitialTheme);
  const [session, setSession] = useState(authGateway.getSession);
  const [isScrolled, setIsScrolled] = useState(false);

  useEffect(() => {
    document.documentElement.style.colorScheme = theme;

    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // Theme still works for the current session when storage is unavailable.
    }
  }, [theme]);

  useEffect(() => {
    const onPopState = () => setView(viewFromLocation());
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  useEffect(() => authGateway.subscribe(setSession), []);

  useEffect(() => {
    const handleScroll = () => setIsScrolled(window.scrollY > 24);
    handleScroll();
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  function navigate(path: string) {
    window.history.pushState({}, "", path);
    startTransition(() => setView(viewFromLocation()));
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function toggleTheme() {
    setTheme((current) => (current === "dark" ? "light" : "dark"));
  }

  const isAuthView = view === "login" || view === "register" || view === "verify";
  const isFsView =
    view === "fs-schedule" ||
    view === "fs-handover" ||
    view === "fs-return" ||
    view === "fs-support" ||
    view === "fs-incidents";

  if (view === "my-storage" && !session) {
    navigate("/login");
    return null;
  }

  return (
    <div className="app-shell" data-theme={theme}>
      <a className="skip-link" href="#main-content">
        Bỏ qua đến nội dung
      </a>
      {isAuthView ? (
        <AuthPage view={view} navigate={navigate} theme={theme} onThemeToggle={toggleTheme} />
      ) : view === "my-storage" ? (
        <CustomerShell
          view={view}
          navigate={navigate}
          theme={theme}
          onThemeToggle={toggleTheme}
          isScrolled={isScrolled}
        >
          <MyStoragePage navigate={navigate} />
        </CustomerShell>
      ) : isFsView ? (
        <FsShell view={view} navigate={navigate} theme={theme} onThemeToggle={toggleTheme}>
          {view === "fs-schedule" && <DailySchedule navigate={navigate} />}
          {view === "fs-handover" && <OnsiteHandoverChecklist navigate={navigate} />}
          {view === "fs-return" && <OnsiteReturnChecklist navigate={navigate} />}
          {view === "fs-support" && <SupportRequestHandling navigate={navigate} />}
          {view === "fs-incidents" && <IncidentReportForm navigate={navigate} />}
        </FsShell>
      ) : (
        <>
          <PublicHeader view={view} navigate={navigate} theme={theme} onThemeToggle={toggleTheme} />
          <main id="main-content">
            {view === "home" && <LandingPage navigate={navigate} />}
            {view === "units" && <BrowsePage navigate={navigate} />}
            {view === "request" && <RequestPage navigate={navigate} />}
          </main>
        </>
      )}
    </div>
  );
}

export default App;
