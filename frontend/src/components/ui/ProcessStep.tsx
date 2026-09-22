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
    <article className="process-step">
      <div className="process-top">
        <span>{number}</span>
        <span className="process-icon">{icon}</span>
      </div>
      <h3>{title}</h3>
      <p>{text}</p>
    </article>
  );
}
