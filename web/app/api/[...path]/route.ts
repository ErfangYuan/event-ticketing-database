import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
const cookie = "mytix_session";
export async function POST(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> },
) {
  const origin = process.env.MYTIX_WEB_ORIGIN || "http://127.0.0.1:3000";
  if (
    request.headers.get("origin") !== origin ||
    !request.headers.get("content-type")?.startsWith("application/json")
  )
    return NextResponse.json(
      { error: "Use this application from its configured origin." },
      { status: 403 },
    );
  const key = process.env.MYTIX_API_SECRET;
  if (!key || key.length < 32)
    return NextResponse.json(
      { error: "The application server is not configured." },
      { status: 503 },
    );
  const { path } = await context.params;
  if (path.length > 2 || path.some((s) => !/^[a-z0-9-]+$/.test(s)))
    return NextResponse.json({ error: "Unknown route." }, { status: 404 });
  const reader = request.body?.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    if (reader)
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.length;
        if (size > 65536) {
          await reader.cancel();
          return NextResponse.json(
            { error: "Request is too large." },
            { status: 413 },
          );
        }
        chunks.push(value);
      }
    const body = Buffer.concat(chunks).toString("utf8");
    const upstream = await fetch(
      `${process.env.MYTIX_API_URL || "http://127.0.0.1:8081"}/api/${path.join("/")}`,
      {
        method: "POST",
        cache: "no-store",
        headers: {
          "Content-Type": "application/json",
          "X-Mytix-Key": key,
          "X-Mytix-Session": request.cookies.get(cookie)?.value || "",
        },
        body,
        signal: AbortSignal.timeout(90000),
      },
    );
    const data = await upstream.json();
    const token = data.sessionToken;
    delete data.sessionToken;
    const response = NextResponse.json(data, {
      status: upstream.status,
      headers: { "Cache-Control": "no-store" },
    });
    if (token)
      response.cookies.set(cookie, token, {
        httpOnly: true,
        secure: origin.startsWith("https://"),
        sameSite: "strict",
        path: "/",
        maxAge: 21600,
      });
    if (
      upstream.ok &&
      ["auth/logout", "auth/delete", "demo/load", "demo/clear"].includes(
        path.join("/"),
      )
    )
      response.cookies.delete(cookie);
    return response;
  } catch {
    return NextResponse.json(
      {
        error:
          "The MyTix service is unavailable. Check that the Java API is running.",
      },
      { status: 502 },
    );
  }
}
