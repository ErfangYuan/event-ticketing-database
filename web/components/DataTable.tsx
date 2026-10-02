"use client";
import { useState } from "react";
import {
  ArrowDownToLine,
  ChevronLeft,
  ChevronRight,
  Search,
} from "lucide-react";
import { Table, title } from "@/lib/api";

export default function DataTable({
  data,
  onCell,
}: {
  data: Table;
  onCell?: (column: string, value: string, row: (string | null)[]) => void;
}) {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const rows = data.rows.filter((row) =>
    row.some((c) =>
      String(c ?? "")
        .toLowerCase()
        .includes(search.toLowerCase()),
    ),
  );
  const safePage = Math.min(page, Math.max(0, Math.ceil(rows.length / 25) - 1));
  const shown = rows.slice(safePage * 25, (safePage + 1) * 25);
  function download() {
    const csv = [data.columns, ...data.rows]
      .map((row) =>
        row
          .map((c) => {
            const value = String(c ?? "");
            const safe = /^\s*[=+@-]/.test(value) ? "'" + value : value;
            return '"' + safe.replaceAll('"', '""') + '"';
          })
          .join(","),
      )
      .join("\r\n");
    const a = document.createElement("a");
    a.href = URL.createObjectURL(
      new Blob([csv], { type: "text/csv;charset=utf-8" }),
    );
    a.download = "mytix-results.csv";
    a.click();
    URL.revokeObjectURL(a.href);
  }
  return (
    <div className="table-card">
      <div className="table-toolbar">
        <span className="result-count">
          {data.total ?? data.rows.length} records{" "}
          <span className="muted">{data.scope && `· ${data.scope}`}</span>
        </span>
        <div className="table-tools">
          <label className="search-small">
            <Search size={15} />
            <input
              aria-label="Filter loaded rows"
              placeholder="Filter loaded rows…"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </label>
          <button
            className="icon-button"
            onClick={download}
            title="Export loaded results"
            aria-label="Export loaded results"
          >
            <ArrowDownToLine size={16} />
          </button>
        </div>
      </div>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              {data.columns.map((c) => (
                <th key={c}>{title(c)}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {shown.map((row, i) => (
              <tr key={safePage * 25 + i}>
                {row.map((value, j) => (
                  <td key={j}>
                    {value == null ? (
                      <span className="muted">—</span>
                    ) : onCell ? (
                      <button
                        className="cell-button"
                        onClick={() => onCell(data.columns[j], value, row)}
                      >
                        {value}
                      </button>
                    ) : /^(ACTIVE|SCHEDULED|COMPLETED|SOLD)$/.test(value) ? (
                      <span className="badge green">{value.toLowerCase()}</span>
                    ) : /^(CANCELLED|WITHDRAWN)$/.test(value) ? (
                      <span className="badge neutral">
                        {value.toLowerCase()}
                      </span>
                    ) : (
                      value
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!rows.length && (
        <div className="empty-state">
          <Search size={28} />
          <h3>No records found</h3>
          <p>Try different filters or load the demonstration dataset.</p>
        </div>
      )}
      {rows.length > 25 && (
        <div className="table-footer">
          <span>
            {safePage * 25 + 1}–{Math.min((safePage + 1) * 25, rows.length)} of{" "}
            {rows.length} loaded rows
          </span>
          <div>
            <button
              aria-label="Previous result page"
              disabled={!safePage}
              className="icon-button"
              onClick={() => setPage(safePage - 1)}
            >
              <ChevronLeft size={16} />
            </button>
            <button
              aria-label="Next result page"
              disabled={(safePage + 1) * 25 >= rows.length}
              className="icon-button"
              onClick={() => setPage(safePage + 1)}
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
