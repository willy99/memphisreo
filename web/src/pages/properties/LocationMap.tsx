import { useEffect, useRef } from "react";
import { Map as MapLibreMap, Marker, NavigationControl, setWorkerUrl, type MapMouseEvent } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";

// Worker MapLibre шукає себе відносно import.meta.url — після бандлінгу Vite
// цей шлях губиться. Явний URL (Vite копіює файл як asset) працює і в dev, і в build.
setWorkerUrl(workerUrl);

// Векторні тайли OpenFreeMap: без ключа й лімітів, атрибуція обов'язкова.
// URL стилю — з env, щоб у проді замінити на власні/комерційні тайли.
const STYLE_URL = import.meta.env.VITE_MAP_STYLE_URL ?? "https://tiles.openfreemap.org/styles/liberty";
const DEFAULT_CENTER: [number, number] = [30.5234, 50.4501]; // Київ

interface LocationMapProps {
  latitude: number | null;
  longitude: number | null;
  /** Пін поставили/перетягнули на мапі (не з автодоповнення). */
  onPick: (latitude: number, longitude: number) => void;
  label: string;
  /** Підказки MapLibre (жести тощо) мовою інтерфейсу. */
  locale: Record<string, string>;
}

/**
 * Мапа з одним пін-ом. Клік — поставити пін, перетягування — уточнити.
 * Зовнішня зміна координат (вибір з автодоповнення) центрує мапу.
 */
export default function LocationMap({ latitude, longitude, onPick, label, locale }: LocationMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markerRef = useRef<Marker | null>(null);
  // Координати, які поставили на самій мапі: їх не "перелітаємо" — агент уже дивиться туди.
  const pickedRef = useRef<[number, number] | null>(null);
  const onPickRef = useRef((lat: number, lng: number) => {
    pickedRef.current = [lat, lng];
    onPick(lat, lng);
  });
  onPickRef.current = (lat: number, lng: number) => {
    pickedRef.current = [lat, lng];
    onPick(lat, lng);
  };

  useEffect(() => {
    if (!containerRef.current) return;
    const hasPoint = latitude !== null && longitude !== null;
    const map = new MapLibreMap({
      container: containerRef.current,
      style: STYLE_URL,
      center: hasPoint ? [longitude, latitude] : DEFAULT_CENTER,
      zoom: hasPoint ? 16 : 10,
      attributionControl: { compact: true },
      cooperativeGestures: true,
      locale,
    });
    map.addControl(new NavigationControl({ showCompass: false }), "top-right");
    map.on("click", (event: MapMouseEvent) => onPickRef.current(event.lngLat.lat, event.lngLat.lng));
    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
    // Мапа створюється один раз; координати синхронізує ефект нижче.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (latitude === null || longitude === null) {
      markerRef.current?.remove();
      markerRef.current = null;
      return;
    }
    if (!markerRef.current) {
      const marker = new Marker({ draggable: true, color: "#0f6b5c" }).setLngLat([longitude, latitude]).addTo(map);
      marker.on("dragend", () => {
        const position = marker.getLngLat();
        onPickRef.current(position.lat, position.lng);
      });
      markerRef.current = marker;
    } else {
      markerRef.current.setLngLat([longitude, latitude]);
    }
    const picked = pickedRef.current;
    const fromMap = picked !== null && picked[0] === latitude && picked[1] === longitude;
    if (!fromMap) {
      map.easeTo({ center: [longitude, latitude], zoom: Math.max(map.getZoom(), 16), duration: 600 });
    }
  }, [latitude, longitude]);

  return <div ref={containerRef} className="location-map" role="application" aria-label={label} />;
}
