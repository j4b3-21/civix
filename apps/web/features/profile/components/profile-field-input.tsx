"use client";

import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { ProfileFieldDto } from "@/types/api";

const UNSET = "__unset__";

export type FieldDefinition = Pick<ProfileFieldDto, "key" | "label" | "type" | "options" | "unit">;

export function ProfileFieldInput({
  field,
  value,
  onChange,
  id,
  invalid,
  describedBy,
  disabled,
}: {
  field: FieldDefinition;
  value: string;
  onChange: (value: string) => void;
  id: string;
  invalid?: boolean;
  describedBy?: string | undefined;
  disabled?: boolean;
}) {
  const common = { id, "aria-invalid": invalid || undefined, "aria-describedby": describedBy, disabled };
  switch (field.type) {
    case "ENUM":
    case "BOOLEAN": {
      const options = field.type === "BOOLEAN" ? [{ value: "true", label: "Yes" }, { value: "false", label: "No" }] : (field.options ?? []).map((o) => ({ value: o, label: o }));
      return (
        <Select value={value === "" ? UNSET : value} onValueChange={(v) => onChange(v === UNSET ? "" : v)} disabled={disabled}>
          <SelectTrigger {...common}>
            <SelectValue placeholder="Not set" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={UNSET}>Not set</SelectItem>
            {options.map((o) => (
              <SelectItem key={o.value} value={o.value}>
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      );
    }
    case "DATE":
      return <Input type="date" value={value} onChange={(e) => onChange(e.target.value)} {...common} />;
    case "NUMBER":
    case "INTEGER":
      return (
        <div className="relative">
          <Input
            type="text"
            inputMode={field.type === "INTEGER" ? "numeric" : "decimal"}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            className={field.unit ? "pr-14" : undefined}
            {...common}
          />
          {field.unit ? (
            <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-sm text-muted-foreground">{field.unit}</span>
          ) : null}
        </div>
      );
    case "STRING":
      return <Input type="text" value={value} onChange={(e) => onChange(e.target.value)} {...common} />;
  }
}
