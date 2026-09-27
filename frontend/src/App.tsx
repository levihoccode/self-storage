import { startTransition, useEffect, useState } from "react";
import { authGateway } from "./app/auth";
import { AuthPage } from "./pages/auth/AuthPage";
import { BrowsePage } from "./pages/BrowsePage";
import { LandingPage } from "./pages/LandingPage";
import { RequestPage } from "./pages/RequestPage";
import { PublicFooter } from "./components/layout/PublicFooter";
import { PublicHeader } from "./components/layout/PublicHeader";
import { CustomerShell } from "./components/layout/CustomerShell";
import { FmShell } from "./components/layout/FmShell";
import { MyStoragePage } from "./pages/MyStoragePage";
import { RentalRequestQueuePage } from "./pages/fm/RentalRequestQueuePage";
import { ProposalRedoPage } from "./pages/fm/ProposalRedoPage";
import { AppointmentSchedulePage } from "./pages/fm/AppointmentSchedulePage";
import { ReturnRequestQueuePage } from "./pages/fm/ReturnRequestQueuePage";
import { ExtendRequestQueuePage } from "./pages/fm/ExtendRequestQueuePage";
import { SupportRequestQueuePage } from "./pages/fm/SupportRequestQueuePage";
import { StorageUnitManagementPage } from "./pages/fm/StorageUnitManagementPage";
import { StaffListPage } from "./pages/fm/StaffListPage";
import { FacilityReportPage } from "./pages/fm/FacilityReportPage";
import { FacilityInvoicesPage } from "./pages/fm/FacilityInvoicesPage";
import { CustomerContractOverviewPage } from "./pages/fm/CustomerContractOverviewPage";
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

  useEffect(() => authGateway.subscribe(setSession), []);

  function navigate(path: string) {
    window.history.pushState({}, "", path);
    startTransition(() => setView(viewFromLocation()));
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function toggleTheme() {
    setTheme((current) => (current === "dark" ? "light" : "dark"));
  }

  const isAuthView = view === "login" || view === "register" || view === "verify";
  const isFmView =
    view === "fm-rental-requests" ||
    view === "fm-proposal-redo" ||
    view === "fm-appointments" ||
    view === "fm-return-requests" ||
    view === "fm-extend-requests" ||
    view === "fm-support-requests" ||
    view === "fm-storage-units" ||
    view === "fm-staff" ||
    view === "fm-reports" ||
    view === "fm-invoices" ||
    view === "fm-contracts";
  if ((view === "my-storage" || isFmView) && !session) {
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
        <CustomerShell view={view} navigate={navigate} theme={theme} onThemeToggle={toggleTheme}>
          <MyStoragePage navigate={navigate} />
        </CustomerShell>
      ) : isFmView ? (
        <FmShell view={view} navigate={navigate} theme={theme} onThemeToggle={toggleTheme}>
          {view === "fm-rental-requests" && <RentalRequestQueuePage navigate={navigate} />}
          {view === "fm-proposal-redo" && <ProposalRedoPage navigate={navigate} />}
          {view === "fm-appointments" && <AppointmentSchedulePage />}
          {view === "fm-return-requests" && <ReturnRequestQueuePage />}
          {view === "fm-extend-requests" && <ExtendRequestQueuePage />}
          {view === "fm-support-requests" && <SupportRequestQueuePage />}
          {view === "fm-storage-units" && <StorageUnitManagementPage navigate={navigate} />}
          {view === "fm-staff" && <StaffListPage />}
          {view === "fm-reports" && <FacilityReportPage navigate={navigate} />}
          {view === "fm-invoices" && <FacilityInvoicesPage />}
          {view === "fm-contracts" && <CustomerContractOverviewPage navigate={navigate} />}
        </FmShell>
      ) : (
        <>
          <PublicHeader view={view} navigate={navigate} theme={theme} onThemeToggle={toggleTheme} />
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
