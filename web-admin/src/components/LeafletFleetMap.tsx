import React, { useEffect, useRef } from 'react';
import L from 'leaflet';

interface FleetBus {
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
}

export const LeafletFleetMap: React.FC<LeafletFleetMapProps> = ({
  fleet,
  selectedBus,
  onSelectBus,
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const markersRef = useRef<{ [key: string]: L.Marker }>({});

  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    // Default center (NYC / metro coordinates from seed)
    const map = L.map(mapContainerRef.current, {
      center: [40.7128, -74.006],
      zoom: 13,
      zoomControl: true,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    }).addTo(map);

    mapInstanceRef.current = map;

    return () => {
      map.remove();
      mapInstanceRef.current = null;
    };
  }, []);

  // Update markers when fleet or selection changes
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    // Clear previous markers
    Object.values(markersRef.current).forEach((m) => m.remove());
    markersRef.current = {};

    const validBuses = fleet.filter((b) => b.lastLocation != null);

    validBuses.forEach((bus) => {
      const loc = bus.lastLocation!;
      const isSelected = selectedBus?.busId === bus.busId;
      const isStale = loc.isStale;

      const bgColor = isSelected ? '#0284c7' : isStale ? '#f59e0b' : '#10b981';
      const pulseHtml = !isStale ? '<span class="absolute -top-1 -right-1 w-3 h-3 bg-emerald-400 rounded-full animate-ping"></span>' : '';

      const customIcon = L.divIcon({
        className: 'custom-bus-marker',
        html: `
          <div style="position: relative; background: ${bgColor}; color: white; border: 2px solid white; border-radius: 9999px; width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.2); font-weight: bold; font-size: 11px;">
            🚌
            ${pulseHtml}
          </div>
        `,
        iconSize: [36, 36],
        iconAnchor: [18, 18],
      });

      const marker = L.marker([loc.latitude, loc.longitude], { icon: customIcon })
        .addTo(map)
        .bindPopup(`
          <div style="padding: 4px; font-family: sans-serif;">
            <p style="font-weight: bold; margin: 0 0 2px 0; font-size: 13px;">Bus ${bus.busNumber} (${bus.routeCode})</p>
            <p style="margin: 0 0 4px 0; font-size: 11px; color: #64748b;">${bus.routeName}</p>
            <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 4px 0;" />
            <p style="margin: 2px 0; font-size: 11px;"><strong>Driver:</strong> ${bus.driverName}</p>
            <p style="margin: 2px 0; font-size: 11px;"><strong>Speed:</strong> ${loc.speed?.toFixed(1) || 0} km/h</p>
            <p style="margin: 2px 0; font-size: 11px;"><strong>Onboard:</strong> ${bus.currentOnboard} / ${bus.capacity}</p>
          </div>
        `);

      marker.on('click', () => {
        onSelectBus(bus);
      });

      markersRef.current[bus.busId] = marker;
    });

    // Pan to selected bus if exists
    if (selectedBus?.lastLocation) {
      map.setView([selectedBus.lastLocation.latitude, selectedBus.lastLocation.longitude], 14, {
        animate: true,
      });
      const marker = markersRef.current[selectedBus.busId];
      if (marker) {
        marker.openPopup();
      }
    } else if (validBuses.length > 0) {
      const bounds = L.latLngBounds(validBuses.map((b) => [b.lastLocation!.latitude, b.lastLocation!.longitude]));
      map.fitBounds(bounds, { padding: [40, 40], maxZoom: 15 });
    }
  }, [fleet, selectedBus, onSelectBus]);

  return (
    <div className="relative w-full h-full min-h-[420px] rounded-xl overflow-hidden shadow-inner border border-gray-200">
      <div ref={mapContainerRef} className="w-full h-full min-h-[420px]" />
    </div>
  );
};

export default LeafletFleetMap;
