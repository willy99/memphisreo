import { useId, type ReactNode } from "react";

interface FieldProps {
  label: string;
  /** id контрола — для label/aria; генерується, якщо не задано. */
  id?: string;
  required?: boolean;
  hint?: string;
  error?: string | null;
  /** Ключ поля для прокрутки з чек-листа ("rooms", "address.city"). */
  fieldKey?: string;
  wide?: boolean;
  children: (controlId: string, describedBy: string | undefined) => ReactNode;
}

/** Обгортка поля: підпис, позначка обов'язковості, підказка й помилка поруч з полем. */
export function Field({ label, id, required, hint, error, fieldKey, wide, children }: FieldProps) {
  const generated = useId();
  const controlId = id ?? generated;
  const hintId = hint ? `${controlId}-hint` : undefined;
  const errorId = error ? `${controlId}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;

  return (
    <div className={`field${error ? " field-invalid" : ""}${wide ? " field-wide" : ""}`} data-field={fieldKey}>
      <label htmlFor={controlId} className="field-label">
        {label}
        {required && (
          <span className="field-required" aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children(controlId, describedBy)}
      {hint && !error && (
        <span id={hintId} className="field-hint">
          {hint}
        </span>
      )}
      {error && (
        <span id={errorId} className="field-error" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}

interface NumberInputProps {
  id: string;
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  unit?: string;
  decimal?: boolean;
  placeholder?: string;
  describedBy?: string;
  invalid?: boolean;
  required?: boolean;
}

/**
 * Число з одиницею всередині поля. type="text" + inputMode замість type="number":
 * без зміни значення колесом миші й з комою як десятковим роздільником.
 */
export function NumberInput({ id, value, onChange, onBlur, unit, decimal, placeholder, describedBy, invalid, required }: NumberInputProps) {
  return (
    <div className="input-with-unit">
      <input
        id={id}
        type="text"
        inputMode={decimal ? "decimal" : "numeric"}
        autoComplete="off"
        value={value}
        placeholder={placeholder}
        aria-describedby={describedBy}
        aria-invalid={invalid || undefined}
        aria-required={required || undefined}
        onChange={(e) => {
          const cleaned = decimal ? e.target.value.replace(/[^\d.,]/g, "") : e.target.value.replace(/[^\d-]/g, "");
          onChange(cleaned);
        }}
        onBlur={onBlur}
      />
      {unit && <span className="input-unit">{unit}</span>}
    </div>
  );
}

interface ChipOption {
  value: string;
  label: string;
}

/** Один варіант з кількох — радіогрупа у вигляді "чіпів" (стрілки змінюють вибір). */
export function ChipGroup({
  label,
  options,
  value,
  onChange,
  allowClear = true,
  describedBy,
}: {
  label: string;
  options: ChipOption[];
  value: string;
  onChange: (value: string) => void;
  allowClear?: boolean;
  describedBy?: string;
}) {
  return (
    <div className="chip-group" role="radiogroup" aria-label={label} aria-describedby={describedBy}>
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected || (!value && option === options[0]) ? 0 : -1}
            className={`chip${selected ? " chip-selected" : ""}`}
            onClick={() => onChange(selected && allowClear ? "" : option.value)}
            onKeyDown={(e) => {
              const index = options.indexOf(option);
              const delta = e.key === "ArrowRight" || e.key === "ArrowDown" ? 1 : e.key === "ArrowLeft" || e.key === "ArrowUp" ? -1 : 0;
              if (delta) {
                e.preventDefault();
                const next = options[(index + delta + options.length) % options.length];
                onChange(next.value);
                const sibling = (e.currentTarget.parentElement?.children[options.indexOf(next)] as HTMLElement | undefined);
                sibling?.focus();
              }
            }}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

/**
 * Кількість (кімнати, санвузли): чіпи 1..N і "N+", що відкриває поле для
 * більшого числа. Один дотик на телефоні, без клавіатури.
 */
export function CountChips({
  id,
  label,
  value,
  onChange,
  max,
  min = 1,
  describedBy,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  max: number;
  min?: number;
  describedBy?: string;
}) {
  const numeric = value === "" ? null : Number(value);
  const isMore = numeric !== null && numeric >= max;
  const options: ChipOption[] = [];
  for (let n = min; n < max; n++) options.push({ value: String(n), label: String(n) });
  options.push({ value: "more", label: `${max}+` });

  return (
    <div className="count-chips">
      <ChipGroup
        label={label}
        options={options}
        value={isMore ? "more" : value}
        describedBy={describedBy}
        onChange={(v) => onChange(v === "more" ? String(max) : v)}
      />
      {isMore && (
        <input
          id={id}
          className="count-more"
          type="text"
          inputMode="numeric"
          aria-label={label}
          value={value}
          onChange={(e) => onChange(e.target.value.replace(/\D/g, ""))}
        />
      )}
    </div>
  );
}

/** Мультивибір (зручності, комунікації) — перемикачі-чіпи. */
export function ToggleChips({
  options,
  selected,
  onToggle,
}: {
  options: ChipOption[];
  selected: string[];
  onToggle: (value: string) => void;
}) {
  return (
    <div className="chip-group chip-group-wrap">
      {options.map((option) => {
        const on = selected.includes(option.value);
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={on}
            className={`chip chip-toggle${on ? " chip-selected" : ""}`}
            onClick={() => onToggle(option.value)}
          >
            {on && <span aria-hidden="true">✓ </span>}
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
