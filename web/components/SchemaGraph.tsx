"use client";
import { useMemo, useState } from "react";
import {
  ReactFlow,
  Background,
  Controls,
  Handle,
  Position,
  NodeProps,
  Node,
  MarkerType,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Database, KeyRound, ArrowUpRight } from "lucide-react";
import { Entity, Schema, title } from "@/lib/api";
type EntityNode = Node<
  { entity: Entity; count: number; selectedEntity: boolean },
  "entity"
>;
const colors: Record<string, string> = {
  Places: "#459386",
  Programming: "#de8050",
  Accounts: "#7586be",
  Transactions: "#b09153",
};
function EntityCard({ data }: NodeProps<EntityNode>) {
  return (
    <div
      className={`entity-node ${data.selectedEntity ? "active" : ""}`}
      style={{ borderTopColor: colors[data.entity.group] }}
    >
      <Handle type="target" position={Position.Left} />
      <div className="entity-node-title">
        <Database size={14} />
        <strong>{data.entity.name}</strong>
        <span>{data.count.toLocaleString()}</span>
      </div>
      <div className="entity-fields">
        {data.entity.columns.slice(0, 5).map((c) => (
          <div key={c.name}>
            <span>
              {c.primary ? <KeyRound size={10} /> : <i />}
              {c.name}
            </span>
            <small>{c.type.split("(")[0]}</small>
          </div>
        ))}
      </div>
      <div className="entity-node-footer">
        {data.entity.columns.length} columns · {data.entity.group}
      </div>
      <Handle type="source" position={Position.Right} />
    </div>
  );
}
const nodeTypes = { entity: EntityCard };
export default function SchemaGraph({
  schema,
  counts,
  onBrowse,
}: {
  schema: Schema;
  counts: Record<string, number>;
  onBrowse: (table: string) => void;
}) {
  const [selected, setSelected] = useState("performances");
  const [search, setSearch] = useState("");
  const entity = schema.entities.find((e) => e.name === selected)!;
  const nodes = useMemo(() => {
    return schema.entities.map((e, index) => {
      const column = index < 5 ? 0 : index < 10 ? 1 : index < 16 ? 2 : 3;
      const row = index - [0, 5, 10, 16][column];
      return {
        id: e.name,
        type: "entity" as const,
        position: {
          x: column * 360,
          y: row * 230,
        },
        data: {
          entity: e,
          count: counts[e.name] ?? 0,
          selectedEntity: e.name === selected,
        },
        style: {
          opacity: search && !e.name.includes(search.toLowerCase()) ? 0.25 : 1,
        },
      };
    });
  }, [schema, counts, selected, search]);
  const edges = useMemo(
    () =>
      schema.relations.map((r, i) => ({
        id: r.name + i,
        source: r.source,
        target: r.target,
        label:
          r.source === selected || r.target === selected ? r.column : undefined,
        type: "smoothstep",
        animated: r.source === selected,
        markerEnd: { type: MarkerType.ArrowClosed, width: 14, height: 14 },
        style: {
          stroke:
            r.source === selected || r.target === selected
              ? "#cf7845"
              : "#c6cbc6",
          strokeWidth: r.source === selected || r.target === selected ? 2 : 1,
          opacity: r.source === selected || r.target === selected ? 1 : 0.4,
        },
        labelStyle: { fontSize: 10 },
        labelBgStyle: { fill: "#fafaf7" },
      })),
    [schema, selected],
  );
  return (
    <div className="schema-layout">
      <section className="graph-panel">
        <div className="graph-toolbar">
          <div>
            <strong>Entity relationship map</strong>
            <span>
              {schema.entities.length} entities · {schema.relations.length}{" "}
              relationships
            </span>
          </div>
          <input
            aria-label="Find entity in graph"
            placeholder="Find an entity…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="graph-canvas">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            onNodeClick={(_, node) => onBrowse(node.id)}
            onEdgeClick={(_, edge) => onBrowse(edge.source)}
            onSelectionChange={({ nodes }) => {
              if (nodes[0]) setSelected(nodes[0].id);
            }}
            fitView
            minZoom={0.15}
            maxZoom={1.8}
            nodesDraggable={false}
            nodesConnectable={false}
            edgesReconnectable={false}
            deleteKeyCode={null}
            proOptions={{ hideAttribution: true }}
            aria-label="MyTix complete database relationship graph"
          >
            <Background color="#dddeda" gap={22} />
            <Controls showInteractive={false} />
          </ReactFlow>
        </div>
        <div className="graph-legend">
          {Object.entries(colors).map(([name, color]) => (
            <span key={name}>
              <i style={{ background: color }} />
              {name}
            </span>
          ))}
          <small>Click an entity or relationship to browse records</small>
        </div>
      </section>
      <aside className="entity-inspector">
        <span className="eyebrow">Selected entity</span>
        <h2>{title(selected)}</h2>
        <p className="description">
          {counts[selected]?.toLocaleString() ?? 0} records · {entity.group}
        </p>
        <button className="primary" onClick={() => onBrowse(selected)}>
          Browse records <ArrowUpRight size={16} />
        </button>
        <h4>Columns</h4>
        <div className="column-list">
          {entity.columns.map((c) => (
            <div key={c.name}>
              <span>
                {c.primary && <KeyRound size={12} />} {c.name}
              </span>
              <small>
                {c.type}
                {c.privateValue ? " · private" : ""}
              </small>
            </div>
          ))}
        </div>
        <h4>Relationships</h4>
        <div className="relation-list">
          {schema.relations
            .filter((r) => r.source === selected || r.target === selected)
            .map((r, i) => (
              <button
                key={i}
                onClick={() =>
                  onBrowse(r.source === selected ? r.target : r.source)
                }
              >
                <span>{r.source === selected ? r.target : r.source}</span>
                <small>
                  {r.column} → {r.targetColumn}
                </small>
                <ArrowUpRight size={13} />
              </button>
            ))}
        </div>
      </aside>
    </div>
  );
}
