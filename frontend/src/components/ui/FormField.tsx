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
    <div className="field-group">
      <label className="field-label" htmlFor={name}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      <div className="input-wrap">
        {icon}
        <input
          id={name}
          name={name}
          type={type}
          required={required}
          placeholder={placeholder}
          autoComplete={name === "email" ? "email" : undefined}
        />
        {suffix && <span className="input-suffix">{suffix}</span>}
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
    <div className="field-group">
      <label className="field-label" htmlFor={name}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      <div className="input-wrap select-input">
        <select id={name} name={name} required={required}>
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
