export type Table = {
  columns: string[];
  rows: (string | null)[][];
  total?: number;
  nextCursor?: string;
  scope?: string;
};
export type User = {
  id: number;
  name: string;
  email: string;
  role: "CUSTOMER" | "ORGANIZER";
};
export type Column = {
  name: string;
  type: string;
  nullable: boolean;
  generated: boolean;
  primary: boolean;
  indexed: boolean;
  privateValue: boolean;
};
export type Entity = {
  name: string;
  columns: Column[];
  primaryKey: string[];
  group: string;
  editable: boolean;
};
export type Relation = {
  name: string;
  source: string;
  column: string;
  target: string;
  targetColumn: string;
};
export type Schema = { entities: Entity[]; relations: Relation[] };
export async function api<T = Record<string, unknown>>(
  path: string,
  body: unknown = {},
): Promise<T> {
  const response = await fetch("/api/" + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "Request failed.");
  return data as T;
}
export function title(value: string) {
  return value
    .replaceAll("_", " ")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}
