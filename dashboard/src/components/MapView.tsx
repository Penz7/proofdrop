import { useEffect, useImperativeHandle, useRef, type Ref } from "react";
import maplibregl, { type StyleSpecification } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { HUB } from "../lib/types";

// OpenStreetMap raster tiles. Fine for development/demo traffic; use a tile provider for production load.
const OSM_STYLE: StyleSpecification = {
  version: 8,
  sources: {
    osm: {
      type: "raster",
      tiles: ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      tileSize: 256,
      maxzoom: 19,
      attribution: '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors',
    },
  },
  layers: [{ id: "osm", type: "raster", source: "osm" }],
};

export interface MapMarker {
  id: string;
  latitude: number;
  longitude: number;
  color: string;
  /** "dot" for couriers, "square" for orders, "pin" for a picked location. */
  shape?: "dot" | "square" | "pin";
  label?: string;
  popup?: string;
  dimmed?: boolean;
}

export interface MapHandle {
  flyTo: (latitude: number, longitude: number, zoom?: number) => void;
}

function markerElement(m: MapMarker): HTMLElement {
  const el = document.createElement("div");
  el.style.display = "flex";
  el.style.alignItems = "center";
  el.style.gap = "4px";
  el.style.cursor = "pointer";
  el.style.opacity = m.dimmed ? "0.55" : "1";
  const shape = document.createElement("div");
  const size = m.shape === "pin" ? 18 : m.shape === "square" ? 12 : 14;
  shape.style.width = `${size}px`;
  shape.style.height = `${size}px`;
  shape.style.background = m.color;
  shape.style.border = "2px solid white";
  shape.style.boxShadow = "0 1px 4px rgba(0,0,0,.45)";
  shape.style.borderRadius = m.shape === "square" ? "3px" : "50%";
  el.appendChild(shape);
  if (m.label) {
    const label = document.createElement("span");
    label.textContent = m.label;
    label.style.font = "600 11px system-ui, sans-serif";
    label.style.padding = "1px 6px";
    label.style.borderRadius = "6px";
    label.style.background = "rgba(14,26,43,.85)";
    label.style.color = "white";
    label.style.whiteSpace = "nowrap";
    el.appendChild(label);
  }
  return el;
}

export function MapView({
  markers,
  onPick,
  className = "",
  zoom = 14,
  ref,
}: {
  markers: MapMarker[];
  onPick?: (latitude: number, longitude: number) => void;
  className?: string;
  zoom?: number;
  ref?: Ref<MapHandle>;
}) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<maplibregl.Map | null>(null);
  const placed = useRef(new Map<string, { marker: maplibregl.Marker; key: string }>());
  const pickRef = useRef(onPick);
  pickRef.current = onPick;

  useEffect(() => {
    if (!container.current) return;
    const m = new maplibregl.Map({
      container: container.current,
      style: OSM_STYLE,
      center: [HUB.longitude, HUB.latitude],
      zoom,
      attributionControl: { compact: true },
    });
    m.addControl(new maplibregl.NavigationControl({ showCompass: false }), "top-right");
    m.on("click", (e) => {
      const p = e.lngLat.wrap();
      pickRef.current?.(p.lat, p.lng);
    });
    map.current = m;
    const current = placed.current;
    return () => {
      current.clear();
      m.remove();
      map.current = null;
    };
    // The map is created once; `zoom` is only the initial value.
  }, []);

  useEffect(() => {
    if (map.current) map.current.getCanvas().style.cursor = onPick ? "crosshair" : "";
  }, [onPick]);

  // Diff markers by id: move existing ones, rebuild only when their look changes.
  useEffect(() => {
    const m = map.current;
    if (!m) return;
    const seen = new Set<string>();
    for (const mk of markers) {
      seen.add(mk.id);
      // Popup text (e.g. "12s ago") changes often; update it in place so an open popup stays open.
      const key = `${mk.color}|${mk.shape}|${mk.label}|${mk.dimmed}|${mk.popup != null}`;
      const existing = placed.current.get(mk.id);
      if (existing && existing.key === key) {
        existing.marker.setLngLat([mk.longitude, mk.latitude]);
        if (mk.popup != null) existing.marker.getPopup()?.setHTML(mk.popup);
        continue;
      }
      existing?.marker.remove();
      const marker = new maplibregl.Marker({ element: markerElement(mk), anchor: "left", offset: [-7, 0] })
        .setLngLat([mk.longitude, mk.latitude])
        .addTo(m);
      if (mk.popup) marker.setPopup(new maplibregl.Popup({ offset: 14, closeButton: false }).setHTML(mk.popup));
      placed.current.set(mk.id, { marker, key });
    }
    for (const [id, { marker }] of placed.current) {
      if (!seen.has(id)) {
        marker.remove();
        placed.current.delete(id);
      }
    }
  }, [markers]);

  useImperativeHandle(ref, () => ({
    flyTo: (latitude, longitude, z = 16) => map.current?.flyTo({ center: [longitude, latitude], zoom: z }),
  }));

  return <div ref={container} className={`overflow-hidden rounded-xl ${className}`} />;
}

/** Escape text before putting it into a MapLibre popup's HTML. */
export function esc(text: string): string {
  return text.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}
