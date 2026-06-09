import { useRef } from "react";
import { useEditorStore } from "../../store/editorStore";
import { exportPaths, downloadJson } from "../../serialization/exportJson";
import { readJsonFiles } from "../../serialization/importJson";
import { resetIdCounters } from "../../store/editorStore";
import { useT } from "../../i18n/I18nContext";

export function Toolbar() {
  const { t, lang, toggleLang } = useT();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);
  const loadGraph = useEditorStore((s) => s.loadGraph);
  const clearGraph = useEditorStore((s) => s.clearGraph);
  const undo = useEditorStore((s) => s.undo);
  const redo = useEditorStore((s) => s.redo);
  const undoStack = useEditorStore((s) => s.undoStack);
  const redoStack = useEditorStore((s) => s.redoStack);
  const validate = useEditorStore((s) => s.validate);
  const validationErrors = useEditorStore((s) => s.validationErrors);
  const clearValidation = useEditorStore((s) => s.clearValidation);

  const errorCount = validationErrors.filter((e) => e.type === "error").length;
  const warnCount = validationErrors.filter((e) => e.type === "warning").length;

  const handleExport = () => {
    const paths = exportPaths(nodes, edges);
    downloadJson(paths);
  };

  const handleImport = () => {
    fileInputRef.current?.click();
  };

  const handleFilesSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    try {
      const { nodes: importedNodes, edges: importedEdges } = await readJsonFiles(files);
      resetIdCounters();
      loadGraph(importedNodes, importedEdges);
      clearValidation();
    } catch (err) {
      alert(t("msg.importFailed").replace("{error}", String(err)));
    }

    // Reset input so the same file can be re-imported
    e.target.value = "";
  };

  const handleValidate = () => {
    const valid = validate();
    if (valid) {
      alert(t("msg.validationPassed"));
    } else {
      alert(t("msg.validationErrors").replace("{errors}", String(errorCount)).replace("{warnings}", String(warnCount)));
    }
  };

  return (
    <div
      className="toolbar"
      style={{
        display: "flex",
        alignItems: "center",
        gap: 6,
        padding: "6px 12px",
        background: "#16162a",
        borderBottom: "1px solid #333",
      }}
    >
      {/* Logo */}
      <span style={{ fontWeight: "bold", color: "#4caf50", fontSize: 15, marginRight: 8 }}>
        {t("app.title")}
      </span>

      {/* Import/Export */}
      <button className="tb-btn" onClick={handleImport}>{t("btn.import")}</button>
      <button className="tb-btn" onClick={handleExport}>{t("btn.export")}</button>

      <div style={{ width: 1, height: 24, background: "#444", margin: "0 4px" }} />

      {/* Undo/Redo */}
      <button
        className="tb-btn"
        onClick={undo}
        disabled={undoStack.length === 0}
      >
        {t("btn.undo")}
      </button>
      <button
        className="tb-btn"
        onClick={redo}
        disabled={redoStack.length === 0}
      >
        {t("btn.redo")}
      </button>

      <div style={{ width: 1, height: 24, background: "#444", margin: "0 4px" }} />

      {/* Validate */}
      <button className="tb-btn" onClick={handleValidate}>
        {t("btn.validate")}
      </button>
      {errorCount > 0 && (
        <span style={{ color: "#ef5350", fontSize: 12 }}>{errorCount} errors</span>
      )}
      {warnCount > 0 && errorCount === 0 && (
        <span style={{ color: "#ff9800", fontSize: 12 }}>{warnCount} warnings</span>
      )}

      <div style={{ flex: 1 }} />

      {/* Language toggle */}
      <button className="tb-btn" onClick={toggleLang} title={`Switch to ${lang === "zh" ? "English" : "中文"}`}>
        {t("btn.lang")}
      </button>

      {/* Clear */}
      <button
        className="tb-btn"
        onClick={() => {
          if (confirm(t("msg.clearConfirm"))) clearGraph();
        }}
        style={{ color: "#ef5350" }}
      >
        {t("btn.clear")}
      </button>

      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        accept=".json"
        multiple
        style={{ display: "none" }}
        onChange={handleFilesSelected}
      />

      <style>{`
        .tb-btn {
          padding: 4px 10px;
          background: #2a2a4e;
          color: #ccc;
          border: 1px solid #444;
          border-radius: 4px;
          cursor: pointer;
          font-size: 12px;
          white-space: nowrap;
        }
        .tb-btn:hover {
          background: #3a3a5e;
        }
        .tb-btn:disabled {
          opacity: 0.4;
          cursor: default;
        }
      `}</style>
    </div>
  );
}
