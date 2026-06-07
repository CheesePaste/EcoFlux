import { ReactFlowProvider } from "@xyflow/react";
import { Toolbar } from "./components/toolbar/Toolbar";
import { BiomePalette } from "./components/palette/BiomePalette";
import { GraphCanvas } from "./components/canvas/GraphCanvas";
import { PropertyPanel } from "./components/panel/PropertyPanel";
import { PlantDatalists } from "./components/panel/editors/PlantTableEditor";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { I18nProvider } from "./i18n/I18nContext";
import { useEditorStore } from "./store/editorStore";
import { useEffect } from "react";
import "./App.css";

function KeyboardHandler() {
  const undo = useEditorStore((s) => s.undo);
  const redo = useEditorStore((s) => s.redo);

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
      if (e.key === "Escape") {
        useEditorStore.getState().setSelectedId(null);
      }
    };

    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [undo, redo]);

  return null;
}

export default function App() {
  return (
    <ErrorBoundary>
      <I18nProvider>
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
      </I18nProvider>
    </ErrorBoundary>
  );
}
