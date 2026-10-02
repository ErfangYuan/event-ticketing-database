"use client";
import { useState } from "react";
import { useModal } from "@/lib/use-modal";
import { X, ArrowRight, Ticket, LoaderCircle } from "lucide-react";
import { api, User } from "@/lib/api";
import { Fields } from "./Workbench";
import { Field } from "@/lib/forms";
export default function AccountModal({
  user,
  onClose,
  onUser,
}: {
  user: User | null;
  onClose: () => void;
  onUser: (u: User | null) => void;
}) {
  useModal(true, onClose);
  const [tab, setTab] = useState<"login" | "register" | "delete">(
    user ? "delete" : "login",
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [values, setValues] = useState<Record<string, string>>({
    email: "customer003@demo.mytix.test",
    password: "",
    role: "CUSTOMER",
    cardExpiry: "2030-12",
    birthday: "1995-01-01",
  });
  const fields: Field[] =
    tab === "delete"
      ? [{ key: "password", label: "Confirm your password", type: "password" }]
      : [
          { key: "email", label: "Email address", type: "email" },
          { key: "password", label: "Password", type: "password" },
          ...(tab === "register"
            ? [
                { key: "name", label: "Full name" },
                {
                  key: "role",
                  label: "Account type",
                  options: ["CUSTOMER", "ORGANIZER"],
                },
                { key: "address", label: "Address" },
                { key: "birthday", label: "Birthday", type: "date" },
                {
                  key: "cardExpiry",
                  label: "Demo payment expiry",
                  type: "month",
                  when: ["role", ["CUSTOMER"]] as [string, string[]],
                },
              ]
            : []),
        ];
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      if (tab === "delete") {
        await api("auth/delete", { password: values.password });
        onUser(null);
      } else {
        if (tab === "register") await api("auth/register", values);
        const result = await api<{ user: User }>("auth/login", values);
        onUser(result.user);
      }
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="modal-backdrop">
      <section
        className="modal account-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="account-title"
      >
        <div className="section-heading">
          <div className="brand-mark">
            <Ticket size={25} />
          </div>
          <button
            className="icon-button"
            onClick={onClose}
            aria-label="Close account dialog"
          >
            <X size={20} />
          </button>
        </div>
        <span className="eyebrow">Your MyTix workspace</span>
        <h2 id="account-title">
          {tab === "login"
            ? "Welcome back."
            : tab === "register"
              ? "Make it your own."
              : "Delete your account"}
        </h2>
        <p className="description">
          {tab === "delete"
            ? "Active future obligations must be resolved first. Historical transactions remain with an anonymized account."
            : "Local demo accounts. No external provider or real payment card needed."}
        </p>
        {tab !== "delete" && (
          <div className="segmented">
            <button
              className={tab === "login" ? "active" : ""}
              onClick={() => setTab("login")}
            >
              Sign in
            </button>
            <button
              className={tab === "register" ? "active" : ""}
              onClick={() => setTab("register")}
            >
              Create account
            </button>
          </div>
        )}
        <form onSubmit={submit}>
          <Fields fields={fields} values={values} setValues={setValues} />
          {error && (
            <p className="alert error" role="alert">
              {error}
            </p>
          )}
          <button
            className={tab === "delete" ? "danger" : "primary"}
            disabled={busy}
          >
            {busy ? (
              <LoaderCircle className="spin" size={16} />
            ) : (
              <ArrowRight size={16} />
            )}{" "}
            {busy
              ? "Please wait…"
              : tab === "login"
                ? "Sign in"
                : tab === "register"
                  ? "Create account"
                  : "Delete my account"}
          </button>
        </form>
        {tab === "login" && (
          <div className="demo-login">
            <span className="eyebrow">Try the demo</span>
            <p>
              Password: <code>MyTixDemo!42</code>
            </p>
            <button
              onClick={() =>
                setValues({
                  ...values,
                  email: "customer003@demo.mytix.test",
                  password: "MyTixDemo!42",
                })
              }
            >
              Fill customer login
            </button>
            <button
              onClick={() =>
                setValues({
                  ...values,
                  email: "organizer01@demo.mytix.test",
                  password: "MyTixDemo!42",
                })
              }
            >
              Fill organizer login
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
