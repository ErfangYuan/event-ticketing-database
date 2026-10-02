"use client";
import { useCallback, useEffect, useState } from "react";
import {
  Activity,
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  ChartNoAxesCombined,
  Check,
  ChevronRight,
  CircleHelp,
  Database,
  GitBranch,
  LayoutDashboard,
  LoaderCircle,
  LogOut,
  Menu,
  Plus,
  RefreshCw,
  Settings2,
  ShieldCheck,
  Sparkles,
  Ticket,
  Trash2,
  Users,
  X,
} from "lucide-react";
import { api, Schema, Table, User, title } from "@/lib/api";
import { queries, reports, customer, organizer } from "@/lib/forms";
import Workbench from "@/components/Workbench";
import DataTable from "@/components/DataTable";
import ResultChart from "@/components/ResultChart";
import SchemaGraph from "@/components/SchemaGraph";
import Explorer from "@/components/Explorer";
import AccountModal from "@/components/AccountModal";
type Status = {
  database: { counts: Record<string, number>; today: string; timezone: string };
  demoEnabled: boolean;
};
type View =
  | "overview"
  | "tickets"
  | "schema"
  | "data"
  | "queries"
  | "reports"
  | "organizer"
  | "demo";
const navigation: [View, string, typeof Ticket][] = [
  ["overview", "Overview", LayoutDashboard],
  ["tickets", "Events & tickets", Ticket],
  ["schema", "Schema explorer", GitBranch],
  ["data", "Data browser", Database],
  ["queries", "Queries", Activity],
  ["reports", "Reports", ChartNoAxesCombined],
  ["organizer", "Organizer studio", CalendarDays],
];
const headings: Record<View, [string, string]> = {
  overview: [
    "A little closer to your next great show.",
    "Your events, ticket journeys and the data that connects them.",
  ],
  tickets: [
    "Every ticket has a story.",
    "Discover a show, choose your seat and keep your plans in one place.",
  ],
  schema: [
    "See how it all connects.",
    "Explore every entity and relationship behind the ticketing experience.",
  ],
  data: [
    "Get to know your data.",
    "Browse complete records and follow their relationships.",
  ],
  queries: [
    "Find exactly what you need.",
    "Seven ways to discover events, availability and the best seats together.",
  ],
  reports: [
    "Turn activity into insight.",
    "Nine original reports, with clear tables and interactive charts.",
  ],
  organizer: [
    "Make the next show happen.",
    "Plan performances, manage inventory and make informed pricing decisions.",
  ],
  demo: [
    "A fresh start, on demand.",
    "Generate a complete synthetic ticketing world or clear the project tables.",
  ],
};
export default function Page() {
  const [view, setView] = useState<View>("overview");
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<Status | null>(null);
  const [schema, setSchema] = useState<Schema | null>(null);
  const [error, setError] = useState("");
  const [account, setAccount] = useState(false);
  const [mobile, setMobile] = useState(false);
  const [entity, setEntity] = useState("events");
  const [reload, setReload] = useState(0);
  const refresh = useCallback(async () => {
    try {
      const [s, m, me] = await Promise.all([
        api<Status>("status"),
        api<Schema>("schema"),
        api<{ user?: User }>("auth/me"),
      ]);
      setStatus(s);
      setSchema(m);
      setUser(me.user ?? null);
      setError("");
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  function navigate(v: View) {
    setView(v);
    setMobile(false);
    window.scrollTo({ top: 0, behavior: "instant" });
  }
  function browse(table: string) {
    setEntity(table);
    navigate("data");
    setReload((x) => x + 1);
  }
  async function logout() {
    try {
      await api("auth/logout");
      setUser(null);
      setReload((x) => x + 1);
    } catch (e) {
      setError((e as Error).message);
    }
  }
  const counts = status?.database.counts ?? {};
  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobile ? "open" : ""}`}>
        <a
          href="#"
          className="brand"
          onClick={(e) => {
            e.preventDefault();
            navigate("overview");
          }}
        >
          <div className="brand-mark">
            <Ticket size={25} />
          </div>
          <span>
            mytix<span className="brand-dot">.</span>
          </span>
        </a>
        <div className="workspace-label">
          <div className="workspace-avatar">EY</div>
          <div>
            <strong>Event workspace</strong>
            <small>Demo project</small>
          </div>
          <span className="live-dot" />
        </div>
        <span className="nav-label">WORKSPACE</span>
        <nav>
          {navigation.map(([id, label, Icon]) => (
            <button
              key={id}
              className={view === id ? "active" : ""}
              onClick={() => navigate(id)}
            >
              <Icon size={18} />
              <span>{label}</span>
              {id === "queries" && <small>07</small>}
              {id === "reports" && <small>09</small>}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <button
            className={view === "demo" ? "active" : ""}
            onClick={() => navigate("demo")}
          >
            <Settings2 size={18} />
            Demo controls
          </button>
          <div className="sidebar-note">
            <ShieldCheck size={20} />
            <strong>Built for exploring.</strong>
            <p>
              Synthetic data. Real relationships.
              <br />
              All dates use UTC.
            </p>
          </div>
          <div className="sidebar-credit">
            A project by Erfang Yuan <ArrowUpRight size={13} />
          </div>
        </div>
      </aside>
      {mobile && (
        <button
          className="mobile-scrim"
          aria-label="Close navigation"
          onClick={() => setMobile(false)}
        />
      )}
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="icon-button mobile-toggle"
              aria-label="Open navigation"
              onClick={() => setMobile(true)}
            >
              <Menu size={20} />
            </button>
            <span>Workspace</span>
            <ChevronRight size={14} />
            <strong>
              {view === "demo"
                ? "Demo controls"
                : navigation.find((n) => n[0] === view)?.[1]}
            </strong>
          </div>
          <div className="topbar-actions">
            <span className="environment">
              <i />
              Local demo
            </span>
            {user ? (
              <>
                <button
                  className="account-button"
                  onClick={() => setAccount(true)}
                >
                  <span className="user-avatar">
                    {user.name.slice(0, 2).toUpperCase()}
                  </span>
                  <span>
                    {user.name}
                    <small>{user.role.toLowerCase()}</small>
                  </span>
                </button>
                <button
                  className="icon-button"
                  aria-label="Sign out"
                  onClick={logout}
                >
                  <LogOut size={17} />
                </button>
              </>
            ) : (
              <button className="secondary" onClick={() => setAccount(true)}>
                Sign in <ArrowUpRight size={15} />
              </button>
            )}
          </div>
        </header>
        <main>
          <div className="page-heading">
            <div>
              <span className="eyebrow">
                {view === "overview"
                  ? "THE MYTIX WORKSPACE"
                  : view === "schema"
                    ? "THE COMPLETE DATA MODEL"
                    : "EXPLORE / " + view.toUpperCase()}
              </span>
              <h1>{headings[view][0]}</h1>
              <p>{headings[view][1]}</p>
            </div>
            {view === "overview" && (
              <button className="primary" onClick={() => navigate("tickets")}>
                Explore events <ArrowUpRight size={17} />
              </button>
            )}
          </div>
          {error && (
            <div className="alert error" role="alert">
              {error}
              <button onClick={() => void refresh()}>Retry connection</button>
            </div>
          )}
          {!status || !schema ? error ? null : (
            <div className="loading-state">
              <LoaderCircle className="spin" size={24} />
              Connecting to your workspace…
            </div>
          ) : (
            <>
              {view === "overview" && (
                <Overview
                  counts={counts}
                  today={status.database.today}
                  navigate={navigate}
                />
              )}
              {view === "schema" && (
                <SchemaGraph
                  schema={schema}
                  counts={counts}
                  onBrowse={browse}
                />
              )}
              {view === "data" && (
                <Explorer
                  key={entity + reload + (user?.id ?? "guest")}
                  schema={schema}
                  initial={entity}
                  user={user}
                  onChanged={() => void refresh()}
                  onOrganizer={() => navigate("organizer")}
                />
              )}
              {["tickets", "queries", "reports", "organizer"].includes(
                view,
              ) && (
                <Workbench
                  key={view + reload + (user?.id ?? "guest")}
                  tools={
                    view === "tickets"
                      ? customer
                      : view === "queries"
                        ? queries
                        : view === "reports"
                          ? reports
                          : organizer
                  }
                  user={user}
                  onSignIn={() => setAccount(true)}
                  onChanged={() => void refresh()}
                />
              )}
              {view === "demo" && (
                <Demo
                  enabled={status.demoEnabled}
                  counts={counts}
                  onComplete={() => {
                    setUser(null);
                    setReload((x) => x + 1);
                    void refresh();
                  }}
                />
              )}
            </>
          )}
          <footer className="footer">
            <span>
              <Ticket size={14} />
              MyTix · Thoughtfully connected.
            </span>
            <span>
              Java + MySQL <i /> Next.js workspace
            </span>
          </footer>
        </main>
      </div>
      {account && (
        <AccountModal
          user={user}
          onClose={() => setAccount(false)}
          onUser={(u) => {
            setUser(u);
            setReload((x) => x + 1);
            void refresh();
          }}
        />
      )}
    </div>
  );
}
function Overview({
  counts,
  today,
  navigate,
}: {
  counts: Record<string, number>;
  today: string;
  navigate: (v: View) => void;
}) {
  const [revenue, setRevenue] = useState<Table | null>(null);
  const [events, setEvents] = useState<Table | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    const from = new Date(Date.parse(today) - 366 * 86400000)
      .toISOString()
      .slice(0, 10);
    Promise.all([
      api<Table>("report/r1", { params: ["city", from, today, ""] }),
      api<Table>("catalog/events"),
    ])
      .then(([r, e]) => {
        setRevenue(r);
        setEvents(e);
      })
      .catch((e) => setError(e.message));
  }, [today, counts.events]);
  const stats: [string, string, typeof Ticket, string][] = [
    ["events", "Events", CalendarDays, "Across music, sports & arts"],
    ["performances", "Performances", Activity, "Every date, every venue"],
    ["tickets", "Tickets", Ticket, "A complete ownership journey"],
    ["venues", "Venues", Database, "Spaces that bring us together"],
  ];
  return (
    <>
      <div className="stats-grid">
        {stats.map(([key, label, Icon, caption], i) => (
          <article className="stat-card" key={key}>
            <div>
              <span>{label}</span>
              <Icon size={19} />
            </div>
            <strong>
              {(counts[key] ?? 0).toLocaleString()}
            </strong>
            <small>{caption}</small>
          </article>
        ))}
      </div>
      <div className="overview-grid">
        <section className="feature-card">
          <span className="pill">
            <Sparkles size={13} />
            CONNECTED BY DESIGN
          </span>
          <h2>
            One show.
            <br />A world of connections.
          </h2>
          <p>
            Follow an event from its venue and price tiers to the people,
            tickets and stories around it.
          </p>
          <button onClick={() => navigate("schema")}>
            Explore the schema <ArrowUpRight size={16} />
          </button>
          <div className="mini-graph" aria-hidden="true">
            <svg viewBox="0 0 360 240">
              <path d="M180 80 L70 150 M180 80 L290 145 M70 150 L170 205 M290 145 L170 205 M180 80 L75 35 M180 80 L285 25" />
            </svg>
            <span className="mini-node n1">
              <CalendarDays size={16} />
              events
            </span>
            <span className="mini-node n2">
              <Database size={14} />
              venues
            </span>
            <span className="mini-node n3">
              <Activity size={14} />
              performances
            </span>
            <span className="mini-node n4">
              <Ticket size={14} />
              tickets
            </span>
            <span className="mini-node n5">
              <Users size={14} />
              users
            </span>
            <span className="mini-node n6">reviews</span>
          </div>
        </section>
        <section className="panel shortcuts">
          <div className="section-heading">
            <div>
              <span className="eyebrow">A good place to start</span>
              <h3>Make yourself at home</h3>
            </div>
            <ArrowDownRight size={22} />
          </div>
          {(
            [
              [
                "queries",
                "Find your next show",
                "Search by place, date, budget and availability.",
                Activity,
              ],
              [
                "reports",
                "Understand the bigger picture",
                "Revenue, resale behavior and audience voices.",
                ChartNoAxesCombined,
              ],
              [
                "data",
                "Follow the records",
                "Browse 22 connected tables, one row at a time.",
                Database,
              ],
            ] as const
          ).map(([id, heading, description, Icon]) => (
            <button key={id} className="shortcut" onClick={() => navigate(id)}>
              <div className="round-icon">
                <Icon size={19} />
              </div>
              <span>
                <strong>{heading}</strong>
                <small>{description}</small>
              </span>
              <ArrowUpRight size={17} />
            </button>
          ))}
        </section>
      </div>
      {error && <p className="alert error">{error}</p>}
      <div className="overview-lower">
        <div>{revenue && <ResultChart data={revenue} />}</div>
        <section className="panel event-preview">
          <div className="section-heading">
            <div>
              <span className="eyebrow">On the horizon</span>
              <h3>Upcoming events</h3>
            </div>
            <button className="text-button" onClick={() => navigate("tickets")}>
              View all <ArrowRight size={14} />
            </button>
          </div>
          {events?.rows.slice(0, 5).map((r, i) => (
            <button
              className="event-preview-row"
              key={r[0]}
              onClick={() => navigate("tickets")}
            >
              <span className={"event-monogram tone-" + i}>
                {r[2]?.slice(0, 2).toUpperCase()}
              </span>
              <span>
                <strong>{r[1]}</strong>
                <small>
                  {r[2]} · {r[3]}
                </small>
              </span>
              <ArrowUpRight size={15} />
            </button>
          ))}
          {events && !events.rows.length && (
            <div className="empty-state">
              <CalendarDays size={25} />
              <h3>Your next chapter starts here</h3>
              <p>Load demo data or create your first event.</p>
              <button className="secondary" onClick={() => navigate("demo")}>
                Open demo controls
              </button>
            </div>
          )}
        </section>
      </div>
    </>
  );
}
function Demo({
  enabled,
  counts,
  onComplete,
}: {
  enabled: boolean;
  counts: Record<string, number>;
  onComplete: () => void;
}) {
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [done, setDone] = useState("");
  async function run(kind: string) {
    if (
      kind === "clear" &&
      !window.confirm(
        "Clear every record in all 22 MyTix tables? This ends all active demo sessions.",
      )
    )
      return;
    setBusy(kind);
    setError("");
    setDone("");
    try {
      await api("demo/" + kind);
      onComplete();
      setDone(
        kind === "load"
          ? "Your new demo world is ready. Sign in with a demo account to try the workflows."
          : "All project records were cleared. The schema is ready for a fresh start.",
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy("");
    }
  }
  return (
    <div className="demo-page">
      <section className="panel demo-main">
        <div className="round-icon large">
          <Sparkles size={30} />
        </div>
        <span className="eyebrow">A whole world, ready to explore</span>
        <h2>Bring the workspace to life.</h2>
        <p className="description">
          Generate events across four cities, future and past performances,
          customers, orders, cancellations, resales and audience reviews. Dates
          follow today in UTC.
        </p>
        <div className="demo-metrics">
          {[
            ["24", "events"],
            ["72", "performances"],
            ["1,903", "tickets"],
            ["22", "tables"],
          ].map(([value, label]) => (
            <div key={label}>
              <strong>{value}</strong>
              <span>{label}</span>
            </div>
          ))}
        </div>
        <button
          className="primary"
          disabled={!!busy || !enabled}
          onClick={() => void run("load")}
        >
          {busy === "load" ? (
            <LoaderCircle className="spin" size={17} />
          ) : (
            <RefreshCw size={17} />
          )}{" "}
          {busy === "load" ? "Building your demo…" : "Generate fresh demo"}
        </button>
        <p className="caption">
          Replaces all project records and ends existing sessions. Usually takes
          15–30 seconds.
        </p>
        <div className="demo-credentials">
          <strong>Public demo accounts</strong>
          <p>
            Customer: <code>customer003@demo.mytix.test</code>
          </p>
          <p>
            Organizer: <code>organizer01@demo.mytix.test</code>
          </p>
          <p>
            Password: <code>MyTixDemo!42</code>
          </p>
        </div>
      </section>
      <div>
        <section className="panel">
          <span className="eyebrow">Current workspace</span>
          <h3>
            {Object.values(counts)
              .reduce((a, b) => a + b, 0)
              .toLocaleString()}{" "}
            connected records
          </h3>
          <p className="description">
            {counts.users ?? 0} accounts · {counts.orders ?? 0} orders ·{" "}
            {counts.reviews ?? 0} reviews
          </p>
          <div className="trust-item">
            <Check size={16} />
            Synthetic accounts and simulated payments
          </div>
          <div className="trust-item">
            <Check size={16} />
            Atomic replacement with rollback on failure
          </div>
          <div className="trust-item">
            <Check size={16} />
            All foreign-key relationships preserved
          </div>
        </section>
        <section className="panel danger-zone">
          <Trash2 size={23} />
          <h3>Clear the workspace</h3>
          <p>
            Delete all project data while keeping the table structure. You can
            generate another demo whenever you’re ready.
          </p>
          <button
            className="danger-outline"
            disabled={!!busy || !enabled}
            onClick={() => void run("clear")}
          >
            {busy === "clear" ? "Clearing…" : "Delete all data"}
          </button>
        </section>
      </div>
      {!enabled && (
        <p className="alert">
          Demo maintenance is disabled on this installation.
        </p>
      )}
      {error && (
        <p className="alert error" role="alert">
          {error}
        </p>
      )}
      {done && (
        <p className="alert success" role="status">
          {done}
        </p>
      )}
    </div>
  );
}
