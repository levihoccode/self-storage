import { useEffect, useState } from "react";
import { ArrowRight, Menu, X } from "lucide-react";
import { Navigate, View } from "../../app/types";
import { Brand } from "../ui/Brand";
import type { Theme } from "../ui/ThemeToggle";
import { ThemeToggle } from "../ui/ThemeToggle";

export function PublicHeader({
  view,
  navigate,
  theme,
  onThemeToggle,
}: {
  view: View;
  navigate: Navigate;
  theme: Theme;
  onThemeToggle: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);

  useEffect(() => {
    function handleScroll() {
      setIsScrolled(window.scrollY > 24);
    }

    handleScroll();
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  function go(path: string) {
    setOpen(false);
    navigate(path);
  }
  return (
    <header className={`public-header ${isScrolled ? "is-scrolled" : ""}`}>
      <div className="container header-inner">
        <Brand onClick={() => go("/")} />
        <button
          className="icon-button menu-button"
          onClick={() => setOpen((current) => !current)}
          aria-label={open ? "Đóng menu" : "Mở menu"}
          aria-expanded={open}
        >
          {open ? <X size={21} /> : <Menu size={21} />}
        </button>
        <nav className={`public-nav ${open ? "is-open" : ""}`} aria-label="Điều hướng chính">
          <button
            className={view === "units" ? "nav-link active" : "nav-link"}
            onClick={() => go("/units")}
          >
            Phương án kho
          </button>
          <a className="nav-link" href="/#how-it-works" onClick={() => setOpen(false)}>
            Quy trình
          </a>
          <a className="nav-link" href="/#support" onClick={() => setOpen(false)}>
            Điểm kho
          </a>
          <ThemeToggle theme={theme} onToggle={onThemeToggle} />
          <button className="nav-login" onClick={() => go("/login")}>
            Đăng nhập <ArrowRight size={16} />
          </button>
        </nav>
      </div>
    </header>
  );
}
