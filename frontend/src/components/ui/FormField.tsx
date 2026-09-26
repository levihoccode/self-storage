import type { ReactNode } from "react";

type FieldProps = {
  label: string;
  name: string;
  type?: string;
  required?: boolean;
  placeholder?: string;
  icon?: ReactNode;
  suffix?: string;
};

export const FIELD_GROUP = "mb-[17px]";
export const FIELD_LABEL = "mb-[7px] block text-[13px] font-bold text-ink";
export const INPUT_WRAP =
  "relative flex min-h-[46px] items-center gap-2.5 rounded-sm border border-border bg-surface-subtle px-[13px] text-muted focus-within:border-brand focus-within:shadow-[0_0_0_3px_var(--brand-soft)] [&>svg:last-child]:pointer-events-none [&>svg:last-child]:ml-auto";
export const INPUT_ELEMENT =
  "min-h-[43px] w-full min-w-0 appearance-none border-0 bg-transparent text-[14px] text-ink outline-0 placeholder:text-[#7d8882]";

export function FormField({
  label,
  name,
  type = "text",
  required,
  placeholder,
  icon,
  suffix,
}: FieldProps) {
  return (
    <div className={FIELD_GROUP}>
      <label className={FIELD_LABEL} htmlFor={name}>
        {label}
        {required && (
          <span className="text-danger" aria-hidden="true">
            {" *"}
          </span>
        )}
      </label>
      <div className={INPUT_WRAP}>
        {icon}
        <input
          id={name}
          name={name}
          type={type}
          required={required}
          placeholder={placeholder}
          autoComplete={name === "email" ? "email" : undefined}
          className={INPUT_ELEMENT}
        />
        {suffix && <span className="shrink-0 text-[11px] text-muted">{suffix}</span>}
      </div>
    </div>
  );
}

export function SelectField({
  label,
  name,
  options,
  required,
}: {
  label: string;
  name: string;
  options: { value: string; label: string }[];
  required?: boolean;
}) {
  return (
    <div className={FIELD_GROUP}>
      <label className={FIELD_LABEL} htmlFor={name}>
        {label}
        {required && (
          <span className="text-danger" aria-hidden="true">
            {" *"}
          </span>
        )}
      </label>
      <div className={INPUT_WRAP}>
        <select
          id={name}
          name={name}
          required={required}
          className={`${INPUT_ELEMENT} invalid:text-muted`}
        >
          <option value="">Chọn một lựa chọn</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <ChevronDownIcon />
      </div>
    </div>
  );
}

function ChevronDownIcon() {
  return (
    <svg
      aria-hidden="true"
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}
