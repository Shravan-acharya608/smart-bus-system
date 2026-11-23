import React, { useEffect, useRef } from 'react';
import L from 'leaflet';

export interface RouteStopInfo {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  order: number;
  arrival?: string | null;
  departure?: string | null;
  distance?: number;
}

export interface FleetBus {
  tripId: string;
  busId: string;
  busNumber: string;
  registrationNo: string;
  capacity: number;
  currentOnboard: number;
  routeName: string;
  routeCode: string;
  driverName: string;
  conductorName: string;
  hasInconsistency: boolean;
  lastLocation: {
    latitude: number;
    longitude: number;
    speed?: number;
    heading?: number;
    timestamp: string;
    isStale: boolean;
    ageSeconds: number;
  } | null;
}

interface LeafletFleetMapProps {
  fleet: FleetBus[];
  selectedBus: FleetBus | null;
  onSelectBus: (bus: FleetBus) => void;
  routeStops?: RouteStopInfo[];
}

// Default Fallback Stops: Real Karnataka Udupi - Katpadi - Shirva route corridor
const DEFAULT_KARNATAKA_STOPS: RouteStopInfo[] = [
  { id: 'stop-1', name: 'Udupi Service Bus Stand', latitude: 13.3409, longitude: 74.7421, order: 1, arrival: '08:00' },
  { id: 'stop-2', name: 'Kinnimulki', latitude: 13.3235, longitude: 74.7388, order: 2, arrival: '08:12' },
  { id: 'stop-3', name: 'Katpadi Junction', latitude: 13.2848, longitude: 74.7505, order: 3, arrival: '08:24' },
  { id: 'stop-4', name: 'Shankarapura', latitude: 13.2603, longitude: 74.7712, order: 4, arrival: '08:35' },
  { id: 'stop-5', name: 'Shirva Bus Stand', latitude: 13.2188, longitude: 74.8223, order: 5, arrival: '08:50' },
];

export const LeafletFleetMap: React.FC<LeafletFleetMapProps> = ({
  fleet,
  selectedBus,
  onSelectBus,
  routeStops,
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const markersRef = useRef<{ [key: string]: L.Marker }>({});
  const stopMarkersRef = useRef<L.Marker[]>([]);
  const polylineRef = useRef<L.Polyline | null>(null);

  // Initialize Map centered on Udupi, Karnataka, India
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const map = L.map(mapContainerRef.current, {
      center: [13.3409, 74.7421], // Central Udupi Bus Stand
      zoom: 12,
      zoomControl: true,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors | Karnataka Transit',
      maxZoom: 19,
    }).addTo(map);

    mapInstanceRef.current = map;

    return () => {
      map.remove();
      mapInstanceRef.current = null;
    };
  }, []);

  // Render Route Corridor & Stops (General Route representation)
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    // Clear previous stop markers & polyline
    stopMarkersRef.current.forEach((m) => m.remove());
    stopMarkersRef.current = [];
    if (polylineRef.current) {
      polylineRef.current.remove();
      polylineRef.current = null;
    }

    const stopsToRender = (routeStops && routeStops.length > 0) ? routeStops : DEFAULT_KARNATAKA_STOPS;
    const sortedStops = [...stopsToRender].sort((a, b) => a.order - b.order);

    if (sortedStops.length > 0) {
      const latLngs: [number, number][] = sortedStops.map((s) => [s.latitude, s.longitude]);

      // Draw Route Corridor Polyline
      const poly = L.polyline(latLngs, {
        color: '#0284c7',
        weight: 5,
        opacity: 0.85,
        dashArray: undefined,
      }).addTo(map);

      polylineRef.current = poly;

      // Draw Stop Markers along the route
      sortedStops.forEach((stop) => {
        const stopIcon = L.divIcon({
          className: 'custom-stop-marker',
          html: `
            <div style="background: white; color: #0284c7; border: 2px solid #0284c7; border-radius: 9999px; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; box-shadow: 0 2px 4px rgba(0,0,0,0.25); font-weight: bold; font-size: 11px;">
              ${stop.order}
            </div>
          `,
          iconSize: [24, 24],
          iconAnchor: [12, 12],
        });

        const stopMarker = L.marker([stop.latitude, stop.longitude], { icon: stopIcon })
          .addTo(map)
          .bindPopup(`
            <div style="padding: 4px; font-family: sans-serif; font-size: 12px;">
              <p style="font-weight: bold; margin: 0 0 2px 0; color: #0f172a;">Stop ${stop.order}: ${stop.name}</p>
              ${stop.arrival ? `<p style="margin: 0; color: #64748b;">Scheduled Time: <strong>${stop.arrival}</strong></p>` : ''}
              <p style="margin: 2px 0 0 0; color: #0284c7; font-size: 10px;">Route: Udupi - Katpadi - Shirva</p>
            </div>
          `);

        stopMarkersRef.current.push(stopMarker);
      });
    }
  }, [routeStops]);

  // Update Bus Markers (both Live GPS and Scheduled General Route Fallback)
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    // Clear previous bus markers
    Object.values(markersRef.current).forEach((m) => m.remove());
    markersRef.current = {};

    const stops = (routeStops && routeStops.length > 0) ? routeStops : DEFAULT_KARNATAKA_STOPS;
    const allBoundPoints: [number, number][] = stops.map((s) => [s.latitude, s.longitude]);

    fleet.forEach((bus, index) => {
      const isSelected = selectedBus?.busId === bus.busId;
      const hasLiveGps = bus.lastLocation != null;

      // If live GPS exists, use it. Otherwise, place at scheduled stop position (General Route Mode)
      const lat = hasLiveGps
        ? bus.lastLocation!.latitude
        : stops[Math.min(index, stops.length - 1)]?.latitude || 13.3409;
      const lng = hasLiveGps
        ? bus.lastLocation!.longitude
        : stops[Math.min(index, stops.length - 1)]?.longitude || 74.7421;

      allBoundPoints.push([lat, lng]);

      const isStale = bus.lastLocation?.isStale;
      const bgColor = !hasLiveGps
        ? '#6366f1' // Indigo for Scheduled / GPS Offline mode
        : isSelected
        ? '#0284c7'
        : isStale
        ? '#f59e0b'
        : '#10b981';

      const pulseHtml = (hasLiveGps && !isStale)
        ? '<span class="absolute -top-1 -right-1 w-3 h-3 bg-emerald-400 rounded-full animate-ping"></span>'
        : '';

      const modeBadge = !hasLiveGps
        ? '<span style="position: absolute; bottom: -8px; background: #4338ca; color: white; font-size: 8px; padding: 1px 4px; border-radius: 4px; white-space: nowrap;">SCHEDULED</span>'
        : '';

      const customIcon = L.divIcon({
        className: 'custom-bus-marker',
        html: `
          <div style="position: relative; background: ${bgColor}; color: white; border: 2px solid white; border-radius: 9999px; width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.25); font-weight: bold; font-size: 13px;">
            🚌
            ${pulseHtml}
            ${modeBadge}
          </div>
        `,
        iconSize: [36, 36],
        iconAnchor: [18, 18],
      });

      const popupContent = hasLiveGps
        ? `
          <div style="padding: 4px; font-family: sans-serif;">
            <p style="font-weight: bold; margin: 0 0 2px 0; font-size: 13px;">Bus ${bus.busNumber} (${bus.routeCode})</p>
            <p style="margin: 0 0 4px 0; font-size: 11px; color: #0284c7; font-weight: 600;">${bus.routeName}</p>
            <span style="background: #dcfce7; color: #15803d; font-size: 10px; font-weight: bold; padding: 2px 6px; border-radius: 4px;">🟢 Live GPS Navigation</span>
            <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 6px 0;" />
            <p style="margin: 2px 0; font-size: 11px;"><strong>Driver:</strong> ${bus.driverName}</p>
            <p style="margin: 2px 0; font-size: 11px;"><strong>Speed:</strong> ${bus.lastLocation?.speed?.toFixed(1) || 0} km/h</p>
            <p style="margin: 2px 0; font-size: 11px;"><strong>Onboard:</strong> ${bus.currentOnboard} / ${bus.capacity}</p>
          </div>
        `
        : `
          <div style="padding: 4px; font-family: sans-serif;">
            <p style="font-weight: bold; margin: 0 0 2px 0; font-size: 13px;">Bus ${bus.busNumber} (${bus.routeCode})</p>
            <p style="margin: 0 0 4px 0; font-size: 11px; color: #0284c7; font-weight: 600;">${bus.routeName}</p>
            <span style="background: #e0e7ff; color: #4338ca; font-size: 10px; font-weight: bold; padding: 2px 6px; border-radius: 4px;">📍 Scheduled Route Mode</span>
            <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 6px 0;" />
            <p style="margin: 2px 0; font-size: 11px; color: #64748b;"><em>Driver phone GPS is offline / not shared. Showing general route corridor and stops.</em></p>
            <p style="margin: 2px 0; font-size: 11px;"><strong>Driver:</strong> ${bus.driverName}</p>
            <p style="margin: 2px 0; font-size: 11px;"><strong>Onboard:</strong> ${bus.currentOnboard} / ${bus.capacity}</p>
          </div>
        `;

      const marker = L.marker([lat, lng], { icon: customIcon })
        .addTo(map)
        .bindPopup(popupContent);

      marker.on('click', () => {
        onSelectBus(bus);
      });

      markersRef.current[bus.busId] = marker;
    });

    // Auto center / fit bounds
    if (selectedBus?.lastLocation) {
      map.setView([selectedBus.lastLocation.latitude, selectedBus.lastLocation.longitude], 14, {
        animate: true,
      });
      const marker = markersRef.current[selectedBus.busId];
      if (marker) marker.openPopup();
    } else if (allBoundPoints.length > 0) {
      const bounds = L.latLngBounds(allBoundPoints);
      map.fitBounds(bounds, { padding: [50, 50], maxZoom: 14 });
    }
  }, [fleet, selectedBus, onSelectBus, routeStops]);

  return (
    <div className="relative w-full h-full min-h-[440px] rounded-xl overflow-hidden shadow-inner border border-gray-200">
      <div ref={mapContainerRef} className="w-full h-full min-h-[440px]" />
    </div>
  );
};

export default LeafletFleetMap;
