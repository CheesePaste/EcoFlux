import { useState, useMemo } from "react";
import { useEditorStore } from "../../store/editorStore";
import { BIOME_LIST } from "../../model/biomeData";
import type { BiomeEntry } from "../../model/types";
import { useT } from "../../i18n/I18nContext";

const DIMENSIONS = ["overworld", "nether", "end"] as const;

const DIM_KEY_MAP: Record<string, string> = {
  overworld: "dimension.overworld",
  nether: "dimension.nether",
  end: "dimension.end",
};

export function BiomePalette() {
  const { t } = useT();
  const [search, setSearch] = useState("");
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  const nodes = useEditorStore((s) => s.nodes);
  const addBiomeNode = useEditorStore((s) => s.addBiomeNode);

  const usedBiomes = useMemo(() => new Set(nodes.map((n) => n.data.biomeId)), [nodes]);

  const filtered = useMemo(() => {
    const q = search.toLowerCase();
    return BIOME_LIST.filter((b) => {
      if (q) {
        return (
          b.biomeId.toLowerCase().includes(q) ||
          b.displayName.toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [search]);

  const grouped = useMemo(() => {
    const groups: Record<string, BiomeEntry[]> = {};
    for (const dim of DIMENSIONS) {
      groups[dim] = filtered.filter((b) => b.dimension === dim);
    }
    return groups;
  }, [filtered]);

  const toggleCollapse = (dim: string) => {
    setCollapsed((prev) => ({ ...prev, [dim]: !prev[dim] }));
  };

  const handleAddBiome = (biomeId: string) => {
    addBiomeNode(biomeId);
  };

  const handleDragStart = (e: React.DragEvent, biomeId: string) => {
    e.dataTransfer.setData("application/biomeId", biomeId);
    e.dataTransfer.effectAllowed = "copy";
  };

  return (
    <div className="biome-palette" style={{ width: 220, borderRight: "1px solid #444", display: "flex", flexDirection: "column", background: "#1a1a2e", height: "100%", overflow: "hidden" }}>
      <div style={{ padding: "8px", borderBottom: "1px solid #444" }}>
        <div style={{ fontWeight: "bold", fontSize: 14, color: "#ccc", marginBottom: 6 }}>
          {t("palette.title")}
        </div>
        <input
          type="text"
          placeholder={t("palette.search")}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{
            width: "100%",
            padding: "4px 8px",
            borderRadius: 4,
            border: "1px solid #555",
            background: "#2a2a3e",
            color: "#ddd",
            fontSize: 12,
            boxSizing: "border-box",
          }}
        />
        <div style={{ marginTop: 4, fontSize: 10, color: "#888" }}>
          {t("palette.biomeCount").replace("{count}", String(filtered.length))} ({t("palette.onCanvas").replace("{count}", String(nodes.length))})
        </div>
      </div>

      <div style={{ flex: 1, overflowY: "auto" }}>
        {DIMENSIONS.map((dim) => {
          const items = grouped[dim];
          if (items.length === 0) return null;
          const isCollapsed = collapsed[dim] ?? false;

          return (
            <div key={dim}>
              <div
                onClick={() => toggleCollapse(dim)}
                style={{
                  padding: "6px 10px",
                  background: "#252540",
                  cursor: "pointer",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  fontSize: 12,
                  fontWeight: "bold",
                  color: "#aaa",
                  borderBottom: "1px solid #333",
                  position: "sticky",
                  top: 0,
                  zIndex: 1,
                }}
              >
                <span>{t(DIM_KEY_MAP[dim])}</span>
                <span style={{ fontSize: 10 }}>{isCollapsed ? "▶" : "▼"} {items.length}</span>
              </div>

              {!isCollapsed && (
                <div>
                  {items.map((biome) => {
                    const isUsed = usedBiomes.has(biome.biomeId);
                    return (
                      <div
                        key={biome.biomeId}
                        draggable
                        onDragStart={(e) => handleDragStart(e, biome.biomeId)}
                        onDragEnd={() => {}}
                        onClick={() => handleAddBiome(biome.biomeId)}
                        title={`${biome.displayName}\nTemp: ${biome.defaultTemp}  Downfall: ${biome.defaultDownfall}\n${isUsed ? t("palette.alreadyUsed") : t("palette.clickToAdd")}`}
                        style={{
                          padding: "4px 10px",
                          fontSize: 12,
                          cursor: isUsed ? "default" : "pointer",
                          color: isUsed ? "#666" : "#ccc",
                          opacity: isUsed ? 0.5 : 1,
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "center",
                          borderBottom: "1px solid #2a2a3e",
                          transition: "background 0.15s",
                        }}
                        onMouseEnter={(e) => {
                          if (!isUsed) e.currentTarget.style.background = "#2a2a4e";
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.background = "transparent";
                        }}
                      >
                        <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                          {biome.displayName}
                        </span>
                        <span style={{ fontSize: 9, color: "#888", marginLeft: 4 }}>
                          🌡{biome.defaultTemp.toFixed(1)}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
