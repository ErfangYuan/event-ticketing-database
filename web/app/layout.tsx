import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = {
  title: "MyTix — Event workspace",
  description:
    "Explore events, ticket journeys and the data behind every show.",
};
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
