import { ReactFlowProvider } from "@xyflow/react";
import { Toolbar } from "./components/toolbar/Toolbar";
import { BiomePalette } from "./components/palette/BiomePalette";
import { GraphCanvas } from "./components/canvas/GraphCanvas";
import { PropertyPanel } from "./components/panel/PropertyPanel";
import { PlantDatalists } from "./components/panel/editors/PlantTableEditor";
import { useEditorStore } from "./store/editorStore";
import { useEffect } from "react";
import "./App.css";

function KeyboardHandler() {
  const undo = useEditorStore((s) => s.undo);
  const redo = useEditorStore((s) => s.redo);
  const removeNode = useEditorStore((s) => s.removeNode);
  const removeEdge = useEditorStore((s) => s.removeEdge);
  const selectedId = useEditorStore((s) => s.selectedId);
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (
        target.tagName === "INPUT" ||
        target.tagName === "SELECT" ||
        target.tagName === "TEXTAREA" ||
        target.isContentEditable
      ) {
        return;
      }

      if ((e.ctrlKey || e.metaKey) && e.key === "z") {
        e.preventDefault();
        undo();
      }
      if ((e.ctrlKey || e.metaKey) && e.key === "y") {
        e.preventDefault();
        redo();
      }
      if (e.key === "Delete" || e.key === "Backspace") {
        if (selectedId) {
          const isNode = nodes.some((n) => n.id === selectedId);
          const isEdge = edges.some((ed) => ed.id === selectedId);
          if (isNode) removeNode(selectedId);
          else if (isEdge) removeEdge(selectedId);
        }
      }
      if (e.key === "Escape") {
        useEditorStore.getState().setSelectedId(null);
      }
    };

    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [undo, redo, removeNode, removeEdge, selectedId, nodes, edges]);

  return null;
}

export default function App() {
  return (
    <ReactFlowProvider>
      <PlantDatalists />
      <KeyboardHandler />
      <div className="app-container">
        <Toolbar />
        <div className="main-content">
          <BiomePalette />
          <GraphCanvas />
          <PropertyPanel />
        </div>
      </div>
    </ReactFlowProvider>
  );
}
