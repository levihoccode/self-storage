import type { ReactNode } from "react";

export function ProcessStep({
  number,
  title,
  text,
  icon,
}: {
  number: string;
  title: string;
  text: string;
  icon: ReactNode;
}) {
  return (
    <article className="grid gap-3">
      <div className="flex items-center justify-between border-b border-border pb-3 font-mono text-[10px] text-muted">
        <span>{number}</span>
        <span className="grid h-8 w-8 place-items-center rounded-full border border-border text-accent">
          {icon}
        </span>
      </div>
      <h3 className="m-0 text-[15px] font-bold tracking-[-0.02em] text-ink">{title}</h3>
      <p className="m-0 text-[13px] leading-relaxed text-muted">{text}</p>
    </article>
  );
}
