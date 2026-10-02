"use client";
import { useState } from "react";
import { Table, title } from "@/lib/api";
export default function ResultChart({ data }: { data: Table }) {
  const candidates = data.columns.filter(
    (c, i) =>
      !/(^rank$|id$|date|time|year|^from|^to)/i.test(c) &&
      data.rows.some((r) => /^-?\d+(\.\d+)?%?$/.test(r[i] ?? "")),
  );
  const [chosen, setChosen] = useState("");
  const metric = candidates.includes(chosen) ? chosen : candidates.at(-1);
  if (!metric || !data.rows.length) return null;
  const index = data.columns.indexOf(metric);
  const preferred = [
    "event",
    "venue",
    "city",
    "customer",
    "organizer",
    "section",
    "tier",
    "nounPhrase",
    "genre",
    "segment",
    "country",
  ].find((c) => data.columns.includes(c));
  const labelIndex = preferred
    ? data.columns.indexOf(preferred)
    : data.columns.findIndex(
        (c, i) =>
          i !== index &&
          !/(id$|rank|date|time)/i.test(c) &&
          data.rows.some((r) => r[i] && !/^-?[\d.]+%?$/.test(r[i]!)),
      );
  const rows = data.rows.slice(0, 12);
  const max = Math.max(
    1,
    ...rows.map((r) => Math.abs(parseFloat(r[index] ?? "0") || 0)),
  );
  return (
    <section className="chart-panel">
      <div className="section-heading">
        <div>
          <span className="eyebrow">Visual perspective</span>
          <h3>{title(metric)}</h3>
        </div>
        <select
          aria-label="Chart metric"
          value={metric}
          onChange={(e) => setChosen(e.target.value)}
        >
          {candidates.map((c) => (
            <option key={c}>{c}</option>
          ))}
        </select>
      </div>
      <div className="bars">
        {rows.map((r, i) => (
          <div className="bar-row" key={i}>
            <span title={r[labelIndex] ?? ""}>
              {labelIndex < 0 ? `Record ${i + 1}` : r[labelIndex]}
            </span>
            <div className="bar-track">
              <div
                className={`bar ${i % 3 === 1 ? "secondary" : ""}`}
                style={{
                  width: `${(Math.abs(parseFloat(r[index] ?? "0") || 0) / max) * 100}%`,
                }}
              />
            </div>
            <strong>{r[index]}</strong>
          </div>
        ))}
      </div>
      <p className="caption">
        {data.rows.length > 12 ? "First 12 records · " : ""}Chart uses the same
        values as the result table.
      </p>
    </section>
  );
}
