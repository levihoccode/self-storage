import { startTransition, useEffect, useState } from "react";
import { authGateway, type AuthSession } from "./app/auth";
import { AUTH_SESSION_EXPIRED_EVENT } from "./app/api";
import { AuthPage } from "./pages/auth/AuthPage";
import { BrowsePage } from "./pages/BrowsePage";
import { LandingPage } from "./pages/LandingPage";
import { RequestPage } from "./pages/RequestPage";
import { PublicFooter } from "./components/layout/PublicFooter";
import { PublicHeader } from "./components/layout/PublicHeader";
import { CustomerShell } from "./components/layout/CustomerShell";
import { MyStoragePage } from "./pages/MyStoragePage";
import { ContractDetailPage } from "./pages/ContractDetailPage";
import { ProposalsPage } from "./pages/ProposalsPage";
import { InvoicesPage } from "./pages/InvoicesPage";
import { AppointmentBookingPage } from "./pages/AppointmentBookingPage";
import { NotificationsPage } from "./pages/NotificationsPage";
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
  const [session, setSession] = useState<AuthSession | null>(null);
  const [isAuthLoading, setIsAuthLoading] = useState(true);

  useEffect(() => {
    document.documentElement.style.colorScheme = theme;
    document.documentElement.dataset.theme = theme;

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

  useEffect(() => {
    function onSessionExpired() {
      authGateway.logout();
      window.history.replaceState({}, "", "/login");
      startTransition(() => setView(viewFromLocation()));
      window.scrollTo({ top: 0, behavior: "smooth" });
    }

    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, onSessionExpired);
    return () => window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, onSessionExpired);
  }, []);

  useEffect(() => {
    let isActive = true;
    const unsubscribe = authGateway.subscribe((nextSession) => {
      if (isActive) setSession(nextSession);
    });

    authGateway
      .restoreSession()
      .then((restoredSession) => {
        if (isActive) setSession(restoredSession);
      })
      .catch(() => {
        if (isActive) setSession(null);
      })
      .finally(() => {
        if (isActive) setIsAuthLoading(false);
      });

    return () => {
      isActive = false;
      unsubscribe();
    };
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
  const isCustomerView =
    view === "my-storage" ||
    view === "contract-detail" ||
    view === "proposals" ||
    view === "invoices" ||
    view === "appointments" ||
    view === "notifications" ||
    (view === "request" && !!session);
  const needsLoginRedirect =
    !isAuthLoading && isCustomerView && (!session || session.user.role !== "CUSTOMER");

  useEffect(() => {
    if (needsLoginRedirect) {
      if (session && session.user.role !== "CUSTOMER") {
        authGateway.logout();
      }
      navigate("/login");
    }
  }, [needsLoginRedirect, session]);

  if (needsLoginRedirect) {
    return null;
  }
  if (isAuthLoading) {
    return (
      <div className="grid min-h-screen place-items-center bg-background text-muted" role="status">
        Đang tải phiên đăng nhập…
      </div>
    );
  }
  return (
    <div className="app-shell" data-theme={theme}>
      <a className="skip-link" href="#main-content">
        Bỏ qua đến nội dung
      </a>
      {isAuthView ? (
        <AuthPage view={view} navigate={navigate} theme={theme} onThemeToggle={toggleTheme} />
      ) : isCustomerView ? (
        <CustomerShell view={view} navigate={navigate} theme={theme} onThemeToggle={toggleTheme}>
          {view === "my-storage" && <MyStoragePage navigate={navigate} />}
          {view === "contract-detail" && <ContractDetailPage navigate={navigate} />}
          {view === "proposals" && <ProposalsPage navigate={navigate} />}
          {view === "invoices" && <InvoicesPage navigate={navigate} />}
          {view === "appointments" && <AppointmentBookingPage navigate={navigate} />}
          {view === "notifications" && <NotificationsPage navigate={navigate} />}
          {view === "request" && <RequestPage navigate={navigate} embedded />}
        </CustomerShell>
      ) : (
        <>
          <PublicHeader
            view={view}
            navigate={navigate}
            theme={theme}
            onThemeToggle={toggleTheme}
            session={session}
          />
          <main id="main-content">
            {view === "home" && <LandingPage navigate={navigate} />}
            {view === "units" && <BrowsePage navigate={navigate} />}
            {view === "request" && <RequestPage navigate={navigate} />}
          </main>
          <PublicFooter navigate={navigate} />
        </>
      )}
    </div>
  );
}

export default App;
