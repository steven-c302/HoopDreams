import "@fontsource/bungee";
import "@fontsource/press-start-2p";
import "./arcade.css";
import { useEffect, type ReactNode } from "react";

/** Switches the page to the arcade look while mounted, leaving Steven's pastel hat page alone. */
export function ArcadeSurface({ children }: { children: ReactNode }) {
  useEffect(() => {
    document.body.dataset.surface = "arcade";
    return () => {
      delete document.body.dataset.surface;
    };
  }, []);
  return <div className="arcade">{children}</div>;
}
