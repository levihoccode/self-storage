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
  const navLink =
    "border-0 bg-transparent text-[13px] font-[650] text-muted transition-colors duration-[180ms] ease hover:text-brand max-[760px]:p-3 max-[760px]:text-left";

  return (
    <header
      className={`sticky top-3 z-10 mx-auto w-[min(100%,1320px)] rounded-none border border-transparent bg-[var(--header-surface)] transition-[width,background,border-color,border-radius,box-shadow] duration-[220ms] ease max-[760px]:top-2 max-[760px]:w-[calc(100%-16px)] max-[760px]:rounded-md ${
        isScrolled
          ? "w-[min(calc(100%-32px),1080px)] rounded-md border-border bg-[var(--header-surface-scrolled)] shadow-[var(--header-shadow-scrolled)] max-[760px]:w-[calc(100%-24px)]"
          : ""
      }`}
    >
      <div
        className={`container flex min-h-[68px] items-center justify-between px-6 max-[760px]:px-4 ${isScrolled ? "min-h-[56px]" : ""}`}
      >
        <Brand onClick={() => go("/")} compact={isScrolled} />
        <button
          className="hidden h-[38px] w-[38px] place-items-center rounded-sm border border-border bg-surface text-ink max-[760px]:inline-grid"
          onClick={() => setOpen((current) => !current)}
          aria-label={open ? "Đóng menu" : "Mở menu"}
          aria-expanded={open}
        >
          {open ? <X size={21} /> : <Menu size={21} />}
        </button>
        <nav
          className={`flex items-center gap-6 max-[760px]:absolute max-[760px]:left-4 max-[760px]:right-4 max-[760px]:flex-col max-[760px]:items-stretch max-[760px]:rounded-[4px] max-[760px]:border max-[760px]:border-border max-[760px]:bg-surface max-[760px]:p-[9px] max-[760px]:shadow-[0_14px_32px_rgba(0,0,0,0.3)] ${
            isScrolled ? "gap-[18px] max-[760px]:top-[56px]" : "max-[760px]:top-[68px]"
          } ${open ? "max-[760px]:flex" : "max-[760px]:hidden"}`}
          aria-label="Điều hướng chính"
        >
          <button
            className={`${navLink} ${view === "units" ? "text-brand" : ""}`}
            onClick={() => go("/units")}
          >
            Phương án kho
          </button>
          <a className={navLink} href="/#how-it-works" onClick={() => setOpen(false)}>
            Quy trình
          </a>
          <a className={navLink} href="/#support" onClick={() => setOpen(false)}>
            Điểm kho
          </a>
          <ThemeToggle
            theme={theme}
            onToggle={onThemeToggle}
            className="max-[760px]:w-full max-[760px]:flex-row-reverse max-[760px]:justify-between max-[760px]:rounded-[4px] max-[760px]:p-[8px_12px]"
          />
          <button
            className="inline-flex items-center gap-2 rounded-[4px] border border-brand bg-transparent px-[17px] py-[11px] text-[13px] font-[650] text-brand transition-colors duration-[180ms] ease hover:bg-brand hover:text-surface max-[760px]:mt-1 max-[760px]:justify-between max-[760px]:p-3 max-[760px]:text-left"
            onClick={() => go("/login")}
          >
            Đăng nhập <ArrowRight size={16} />
          </button>
        </nav>
      </div>
    </header>
  );
}
