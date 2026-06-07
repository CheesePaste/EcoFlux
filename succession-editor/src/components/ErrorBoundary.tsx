import { Component, type ReactNode } from "react";
import { translations, getSavedLang, type Lang } from "../i18n/translations";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: string;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: "" };
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error("[Ecoflux Editor] React error:", error, errorInfo);
    this.setState({
      errorInfo: errorInfo.componentStack ?? String(errorInfo),
    });
  }

  render() {
    if (this.state.hasError) {
      const lang: Lang = getSavedLang();
      const t = translations[lang];
      return (
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            height: "100vh",
            background: "#12122a",
            color: "#ddd",
            fontFamily: "monospace",
            padding: 40,
            gap: 16,
          }}
        >
          <div style={{ fontSize: 48 }}>💥</div>
          <h2 style={{ color: "#ef5350" }}>{t["error.title"]}</h2>
          <div
            style={{
              background: "#1a1a2e",
              padding: 16,
              borderRadius: 8,
              maxWidth: 600,
              overflow: "auto",
              fontSize: 12,
              color: "#ef9a9a",
              whiteSpace: "pre-wrap",
              border: "1px solid #c62828",
            }}
          >
            {this.state.error?.message ?? "Unknown error"}
          </div>
          <details style={{ maxWidth: 600, fontSize: 11, color: "#888" }}>
            <summary>Stack trace</summary>
            <pre style={{ whiteSpace: "pre-wrap", fontSize: 10 }}>
              {this.state.error?.stack}
              {this.state.errorInfo}
            </pre>
          </details>
          <button
            onClick={() => {
              this.setState({ hasError: false, error: null, errorInfo: "" });
              window.location.reload();
            }}
            style={{
              padding: "8px 20px",
              background: "#2e7d32",
              color: "#fff",
              border: "none",
              borderRadius: 6,
              cursor: "pointer",
              fontSize: 14,
            }}
          >
            {t["error.reload"]}
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
