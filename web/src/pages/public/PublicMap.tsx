import { useEffect, useRef } from "react";
import { Map as MapLibreMap, Marker, NavigationControl, Popup, setWorkerUrl } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";
import type { PublicCard } from "../../api/types";

setWorkerUrl(workerUrl);
const STYLE_URL = import.meta.env.VITE_MAP_STYLE_URL ?? "https://tiles.openfreemap.org/styles/liberty";

interface PublicMapProps {
  items: PublicCard[];
  /** Один об'єкт: показати коло приблизного розташування замість піна. */
  single?: { latitude: number; longitude: number; approximate: boolean } | null;
  activeId?: string | null;
  onSelect?: (id: string) => void;
  formatPrice: (card: PublicCard) => string;
}

/** Мапа результатів: піни з ціною; або одна точка/коло ~400 м на сторінці об'єкта. */
export default function PublicMap({ items, single, activeId, onSelect, formatPrice }: PublicMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markersRef = useRef<Marker[]>([]);
  const loadedRef = useRef(false);

  useEffect(() => {
    if (!containerRef.current) return;
    const map = new MapLibreMap({
      container: containerRef.current,
      style: STYLE_URL,
      center: single ? [single.longitude, single.latitude] : [30.73, 46.47],
      zoom: single ? (single.approximate ? 14 : 16) : 11,
      attributionControl: { compact: true },
      cooperativeGestures: true,
    });
    map.addControl(new NavigationControl({ showCompass: false }), "top-right");
    map.on("load", () => {
      loadedRef.current = true;
      if (single?.approximate) {
        map.addSource("approx", { type: "geojson", data: circle(single.longitude, single.latitude, 400) });
        map.addLayer({ id: "approx-fill", type: "fill", source: "approx", paint: { "fill-color": "#0f6b5c", "fill-opacity": 0.18 } });
        map.addLayer({ id: "approx-line", type: "line", source: "approx", paint: { "line-color": "#0f6b5c", "line-width": 2 } });
      } else if (single) {
        new Marker({ color: "#0f6b5c" }).setLngLat([single.longitude, single.latitude]).addTo(map);
      }
    });
    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || single) return;
    markersRef.current.forEach((m) => m.remove());
    markersRef.current = [];
    const points = items.filter((c) => c.latitude !== null && c.longitude !== null);
    for (const card of points) {
      const el = document.createElement("button");
      el.type = "button";
      el.className = `price-pin${card.id === activeId ? " active" : ""}`;
      el.textContent = formatPrice(card);
      el.addEventListener("click", () => onSelect?.(card.id));
      const marker = new Marker({ element: el, anchor: "bottom" }).setLngLat([card.longitude!, card.latitude!]).addTo(map);
      if (card.title) {
        marker.setPopup(new Popup({ offset: 24, closeButton: false }).setText(card.title));
      }
      markersRef.current.push(marker);
    }
    if (points.length > 0) {
      const lons = points.map((p) => p.longitude!);
      const lats = points.map((p) => p.latitude!);
      map.fitBounds(
        [
          [Math.min(...lons), Math.min(...lats)],
          [Math.max(...lons), Math.max(...lats)],
        ],
        { padding: 48, maxZoom: 15, duration: 500 },
      );
    }
  }, [items, activeId, onSelect, formatPrice, single]);

  return <div ref={containerRef} className="public-map" role="application" aria-label="Map" />;
}

function circle(lon: number, lat: number, radiusM: number) {
  const points = 48;
  const coords: [number, number][] = [];
  for (let i = 0; i <= points; i++) {
    const angle = (i / points) * 2 * Math.PI;
    const dx = (radiusM / 111320) * Math.cos(angle);
    const dy = (radiusM / 110540) * Math.sin(angle);
    coords.push([lon + dx / Math.cos((lat * Math.PI) / 180), lat + dy]);
  }
  return { type: "Feature" as const, properties: {}, geometry: { type: "Polygon" as const, coordinates: [coords] } };
}
