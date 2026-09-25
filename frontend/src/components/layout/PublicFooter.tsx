import { Navigate } from "../../app/types";
import { PAGE_CONTAINER } from "../../app/layout";
import { Brand } from "../ui/Brand";

const footerLink =
  "w-fit border-0 bg-transparent p-0 text-left text-[12px] text-[var(--footer-muted)] hover:text-white";

export function PublicFooter({ navigate }: { navigate: Navigate }) {
  return (
    <footer className="bg-[var(--footer-surface)] text-ink transition-colors duration-[280ms] ease">
      <div
        className={`${PAGE_CONTAINER} flex justify-between gap-20 pb-[55px] pt-[65px] max-[760px]:block max-[760px]:pb-[35px] max-[760px]:pt-[50px]`}
      >
        <div>
          <Brand onClick={() => navigate("/")} />
        </div>
        <div className="flex gap-[100px] max-[980px]:gap-10 max-[760px]:mt-10 max-[760px]:gap-[30px]">
          <div className="grid min-w-[130px] content-start gap-2.5">
            <strong className="mb-[5px] font-mono text-[10px] font-medium uppercase tracking-[0.12em] text-brand">
              Khám phá
            </strong>
            <button className={footerLink} onClick={() => navigate("/units")}>
              Phương án kho
            </button>
            <a className={footerLink} href="/#how-it-works">
              Quy trình
            </a>
            <button className={footerLink} onClick={() => navigate("/rental-requests/new")}>
              Gửi nhu cầu
            </button>
          </div>
          <div className="grid min-w-[130px] content-start gap-2.5">
            <strong className="mb-[5px] font-mono text-[10px] font-medium uppercase tracking-[0.12em] text-brand">
              Hỗ trợ
            </strong>
            <a className={footerLink} href="mailto:hello@khomoc.example">
              Email cho Kho Mộc
            </a>
            <a className={footerLink} href="tel:+842812345678">
              028 1234 5678
            </a>
            <span className={footerLink}>TP. Hồ Chí Minh</span>
          </div>
        </div>
      </div>
      <div
        className={`${PAGE_CONTAINER} flex items-center justify-between gap-4 border-t border-[#e6eec933] py-[17px] font-mono text-[9px] text-[var(--footer-muted)] max-[760px]:block`}
      >
        <span>© 2026 Kho Mộc.</span>
      </div>
    </footer>
  );
}
