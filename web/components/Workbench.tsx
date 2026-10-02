"use client";
import { useState } from "react";
import {
  ArrowRight,
  Play,
  CheckCircle2,
  LockKeyhole,
  LoaderCircle,
} from "lucide-react";
import { Tool, Field } from "@/lib/forms";
import { api, Table, User } from "@/lib/api";
import DataTable from "./DataTable";
import ResultChart from "./ResultChart";
export function Fields({
  fields,
  values,
  setValues,
}: {
  fields: Field[];
  values: Record<string, string>;
  setValues: (v: Record<string, string>) => void;
}) {
  return (
    <div className="field-grid">
      {fields
        .filter((f) => !f.when || f.when[1].includes(values[f.when[0]]))
        .map((f) => (
          <label key={f.key} className={f.type === "textarea" ? "wide" : ""}>
            <span>
              {f.label}
              {f.optional && <small>optional</small>}
            </span>
            {f.options ? (
              <select
                value={values[f.key] ?? ""}
                onChange={(e) =>
                  setValues({ ...values, [f.key]: e.target.value })
                }
              >
                {f.options.map((o) => (
                  <option key={o}>{o}</option>
                ))}
              </select>
            ) : f.type === "textarea" ? (
              <textarea
                rows={3}
                value={values[f.key] ?? ""}
                onChange={(e) =>
                  setValues({ ...values, [f.key]: e.target.value })
                }
                required={!f.optional}
              />
            ) : (
              <input
                type={f.type ?? "text"}
                step={f.type === "number" ? "any" : undefined}
                value={values[f.key] ?? ""}
                onChange={(e) =>
                  setValues({ ...values, [f.key]: e.target.value })
                }
                required={!f.optional}
                autoComplete={
                  f.type === "password" ? "current-password" : undefined
                }
              />
            )}{" "}
            {f.help && <small className="field-help">{f.help}</small>}
          </label>
        ))}
    </div>
  );
}
export default function Workbench({
  tools,
  user,
  onSignIn,
  initialId,
  onChanged,
}: {
  tools: Tool[];
  user: User | null;
  onSignIn: () => void;
  initialId?: string;
  onChanged?: () => void;
}) {
  const [selected, setSelected] = useState(initialId ?? tools[0].id);
  const tool = tools.find((t) => t.id === selected) ?? tools[0];
  return (
    <div className="workbench">
      <aside className="tool-list">
        {tools.map((t) => (
          <button
            key={t.id}
            className={tool.id === t.id ? "selected" : ""}
            onClick={() => setSelected(t.id)}
          >
            <span className="tool-id">
              {t.id.match(/^[qr]\d$/) ? (
                t.id.toUpperCase()
              ) : (
                <ArrowRight size={14} />
              )}
            </span>
            <span>{t.name}</span>
          </button>
        ))}
      </aside>
      <ToolPanel key={tool.id} tool={tool} user={user} onSignIn={onSignIn} onChanged={onChanged} />
    </div>
  );
}
function ToolPanel({
  tool,
  user,
  onSignIn,
  onChanged,
}: {
  tool: Tool;
  user: User | null;
  onSignIn: () => void;
  onChanged?: () => void;
}) {
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(tool.fields.map((f) => [f.key, f.value ?? ""])),
  );
  const [data, setData] = useState<Table | null>(null);
  const [result, setResult] = useState<unknown>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const permitted = !tool.role || user?.role === tool.role;
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setData(null);
    setResult(null);
    try {
      const r = await api<Table & { result?: unknown }>(
        tool.path,
        tool.params ? tool.params(values) : values,
      );
      if (r.columns) setData(r);
      else setResult(r.result ?? r);
      if (!tool.read) onChanged?.();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="tool-content">
      <section className="panel">
        <div className="section-heading">
          <div>
            <span className="eyebrow">
              {tool.id.match(/^[qr]\d$/)
                ? tool.id.toUpperCase()
                : tool.read
                  ? "Explore"
                  : "Take action"}
            </span>
            <h2>{tool.name}</h2>
          </div>
          <div className="round-icon">
            <Play size={20} />
          </div>
        </div>
        <p className="description">{tool.description}</p>
        <form onSubmit={submit}>
          <Fields fields={tool.fields} values={values} setValues={setValues} />
          {permitted ? (
            <button className="primary" disabled={busy}>
              {busy ? (
                <LoaderCircle className="spin" size={16} />
              ) : (
                <ArrowRight size={16} />
              )}{" "}
              {busy ? "Working…" : tool.read ? "Run view" : "Submit"}
            </button>
          ) : (
            <div className="auth-notice">
              <LockKeyhole size={18} />
              <span>
                Use a {tool.role?.toLowerCase()} account for this workflow.
              </span>
              <button type="button" onClick={onSignIn}>
                Sign in
              </button>
            </div>
          )}
        </form>
        {error && (
          <p className="alert error" role="alert">
            {error}
          </p>
        )}
        {result !== null && (
          <div className="alert success" role="status">
            <CheckCircle2 size={18} />
            Completed{typeof result === "number" ? ` · Result: ${result}` : ""}.
          </div>
        )}
      </section>
      {data && (
        <>
          <ResultChart data={data} />
          <DataTable data={data} />
        </>
      )}
    </div>
  );
}
