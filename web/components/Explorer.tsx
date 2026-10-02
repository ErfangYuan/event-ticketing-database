"use client";
import { useCallback, useEffect, useState } from "react";
import { useModal } from "@/lib/use-modal";
import {
  Database,
  Plus,
  ArrowLeft,
  ArrowRight,
  Trash2,
  X,
  Link2,
  LoaderCircle,
} from "lucide-react";
import { api, Table, Schema, User, title } from "@/lib/api";
import DataTable from "./DataTable";
import { Fields } from "./Workbench";
export default function Explorer({
  schema,
  initial,
  user,
  onChanged,
  onOrganizer,
}: {
  schema: Schema;
  initial: string;
  user: User | null;
  onChanged: () => void;
  onOrganizer: () => void;
}) {
  const [name, setName] = useState(initial);
  const [data, setData] = useState<Table | null>(null);
  const [cursor, setCursor] = useState("");
  const [stack, setStack] = useState<string[]>([]);
  const [filter, setFilter] = useState({ column: "", value: "" });
  const [draft, setDraft] = useState({ column: "", value: "" });
  const [limit, setLimit] = useState("25");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<(string | null)[] | null>(null);
  const [creating, setCreating] = useState(false);
  const closeCreate = useCallback(() => setCreating(false), []);
  useModal(creating, closeCreate);
  const [values, setValues] = useState<Record<string, string>>({});
  const [version, setVersion] = useState(0);
  const [notice, setNotice] = useState("");
  const entity = schema.entities.find((e) => e.name === name)!;
  useEffect(() => {
    let alive = true;
    setBusy(true);
    setData(null);
    setError("");
    api<Table>("table", {
      table: name,
      limit,
      cursor,
      filterColumn: filter.column,
      filterValue: filter.value,
    })
      .then((d) => {
        if (alive) setData(d);
      })
      .catch((e) => {
        if (alive) setError(e.message);
      })
      .finally(() => {
        if (alive) setBusy(false);
      });
    return () => {
      alive = false;
    };
  }, [name, limit, cursor, filter, version]);
  function browse(table: string, column = "", value = "") {
    setName(table);
    setCursor("");
    setStack([]);
    setFilter({ column, value });
    setDraft({ column, value });
    setSelected(null);
    setCreating(false);
    setNotice("");
  }
  async function mutate(remove: boolean) {
    setBusy(true);
    setError("");
    try {
      const supplied = remove
        ? Object.fromEntries(
            entity.primaryKey.map((key) => [
              key,
              selected![data!.columns.indexOf(key)],
            ]),
          )
        : Object.fromEntries(
            Object.entries(values).filter(([, v]) => v !== ""),
          );
      await api("reference/" + (remove ? "delete" : "create"), {
        table: name,
        values: supplied,
      });
      setCreating(false);
      setSelected(null);
      setCursor("");
      setStack([]);
      setVersion((v) => v + 1);
      setNotice(remove ? "Record deleted." : "Record created.");
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  const fields = entity.columns
    .filter((c) => !c.generated && !c.privateValue)
    .map((c) => ({
      key: c.name,
      label: title(c.name),
      optional: c.nullable,
      type: /int|decimal|double|float/i.test(c.type) ? "number" : "text",
      help: c.primary ? "Primary key" : undefined,
    }));
  return (
    <div className="explorer-layout">
      <aside className="entity-list">
        {["Places", "Programming", "Accounts", "Transactions"].map((group) => (
          <div key={group}>
            <span className="eyebrow">{group}</span>
            {schema.entities
              .filter((e) => e.group === group)
              .map((e) => (
                <button
                  className={e.name === name ? "selected" : ""}
                  key={e.name}
                  onClick={() => browse(e.name)}
                >
                  <Database size={14} />
                  {title(e.name)}
                </button>
              ))}
          </div>
        ))}
      </aside>
      <div className="explorer-content">
        <div className="section-heading">
          <div>
            <span className="eyebrow">{entity.group} / Entity</span>
            <h2>{title(name)}</h2>
            <p className="description">
              {entity.columns.length} columns · ordered by{" "}
              {entity.primaryKey.join(", ")}
            </p>
          </div>
          {entity.editable && user?.role === "ORGANIZER" && (
            <button
              className="primary"
              onClick={() =>
                name === "events"
                  ? onOrganizer()
                  : (setCreating(true), setValues({}))
              }
            >
              <Plus size={16} />
              New record
            </button>
          )}
        </div>
        <form
          className="filter-strip"
          onSubmit={(e) => {
            e.preventDefault();
            setCursor("");
            setStack([]);
            setFilter(draft);
          }}
        >
          <select
            aria-label="Indexed filter column"
            value={draft.column}
            onChange={(e) => setDraft({ ...draft, column: e.target.value })}
          >
            <option value="">All records</option>
            {entity.columns
              .filter((c) => c.indexed && !c.privateValue)
              .map((c) => (
                <option key={c.name}>{c.name}</option>
              ))}
          </select>
          <input
            aria-label="Filter value"
            placeholder="Exact value"
            disabled={!draft.column}
            value={draft.value}
            onChange={(e) => setDraft({ ...draft, value: e.target.value })}
          />
          <button className="secondary">Apply filter</button>
          <select
            aria-label="Page size"
            value={limit}
            onChange={(e) => {
              setLimit(e.target.value);
              setCursor("");
              setStack([]);
            }}
          >
            {["10", "25", "50", "100"].map((n) => (
              <option value={n} key={n}>
                {n} / page
              </option>
            ))}
          </select>
        </form>
        {error && (
          <p className="alert error" role="alert">
            {error}
          </p>
        )}
        {notice && (
          <p className="alert success" role="status">
            {notice}
          </p>
        )}
        {busy && !data && (
          <div className="loading-state">
            <LoaderCircle size={22} className="spin" />
            Loading records…
          </div>
        )}
        {data && (
          <>
            <DataTable
              data={data}
              onCell={(column, value, row) => {
                setSelected(row);
                setNotice("");
              }}
            />
            <div className="table-footer">
              <span>
                Page {stack.length + 1} · {data.scope}
              </span>
              <div>
                <button
                  className="secondary"
                  disabled={busy || !stack.length}
                  onClick={() => {
                    setCursor(stack.at(-1)!);
                    setStack(stack.slice(0, -1));
                    setSelected(null);
                  }}
                >
                  <ArrowLeft size={14} />
                  Previous
                </button>
                <button
                  className="secondary"
                  disabled={busy || !data.nextCursor}
                  onClick={() => {
                    setStack([...stack, cursor]);
                    setCursor(data.nextCursor!);
                    setSelected(null);
                  }}
                >
                  Next
                  <ArrowRight size={14} />
                </button>
              </div>
            </div>
          </>
        )}
        {selected && data && (
          <section className="panel row-detail">
            <div className="section-heading">
              <h3>Record details</h3>
              <button
                className="icon-button"
                aria-label="Close record details"
                onClick={() => setSelected(null)}
              >
                <X size={16} />
              </button>
            </div>
            <dl>
              {data.columns.map((c, i) => (
                <div key={c}>
                  <dt>{title(c)}</dt>
                  <dd>{selected[i] ?? "—"}</dd>
                </div>
              ))}
            </dl>
            <div className="related-links">
              {schema.relations
                .filter(
                  (r) =>
                    r.source === name &&
                    !entity.columns.find((c) => c.name === r.column)
                      ?.privateValue,
                )
                .map((r, i) => (
                  <button
                    className="secondary"
                    key={i}
                    onClick={() =>
                      browse(
                        r.target,
                        r.targetColumn,
                        selected[data.columns.indexOf(r.column)] ?? "",
                      )
                    }
                    disabled={selected[data.columns.indexOf(r.column)] == null}
                  >
                    <Link2 size={14} />
                    {title(r.target)}
                  </button>
                ))}
              {schema.relations
                .filter(
                  (r) =>
                    r.target === name &&
                    !entity.columns.find((c) => c.name === r.targetColumn)
                      ?.privateValue,
                )
                .map((r, i) => (
                  <button
                    className="secondary"
                    key={"child" + i}
                    onClick={() =>
                      browse(
                        r.source,
                        r.column,
                        selected[data.columns.indexOf(r.targetColumn)] ?? "",
                      )
                    }
                  >
                    <Link2 size={14} />
                    {title(r.source)} via {r.column}
                  </button>
                ))}
            </div>
            {entity.editable && user?.role === "ORGANIZER" && (
              <button
                className="danger-outline"
                disabled={busy}
                onClick={() => {
                  if (
                    window.confirm(
                      "Delete this record? Existing references will prevent deletion.",
                    )
                  )
                    void mutate(true);
                }}
              >
                <Trash2 size={14} />
                Delete record
              </button>
            )}
          </section>
        )}
        {creating && (
          <div className="modal-backdrop">
            <section
              className="modal"
              role="dialog"
              aria-modal="true"
              aria-labelledby="new-row-title"
            >
              <div className="section-heading">
                <h2 id="new-row-title">New {title(name)} record</h2>
                <button
                  className="icon-button"
                  aria-label="Close new record"
                  onClick={() => setCreating(false)}
                >
                  <X size={20} />
                </button>
              </div>
              <p className="description">
                Required relationships and uniqueness rules are checked before
                saving.
              </p>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  void mutate(false);
                }}
              >
                <Fields fields={fields} values={values} setValues={setValues} />
                {error && <p className="alert error">{error}</p>}
                <button className="primary" disabled={busy}>
                  <Plus size={16} />
                  Create record
                </button>
              </form>
            </section>
          </div>
        )}
      </div>
    </div>
  );
}
