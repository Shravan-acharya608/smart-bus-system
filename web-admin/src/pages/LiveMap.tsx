import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Bus, AlertTriangle, Users, Compass, RefreshCw, Info, Navigation, MapPin } from 'lucide-react';
import { apiClient } from '../lib/axios';
import { ApiResponse } from '../types';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import LeafletFleetMap, { RouteStopInfo, FleetBus } from '../components/LeafletFleetMap';

interface DashboardResponse {
  fleetStatus: FleetBus[];
}

interface IncidentItem {
  id: string;
  tripId: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  description: string;
  delayMinutes: number;
  isResolved: boolean;
  trip?: {
    bus?: { busNumber: string };
    route?: { name: string };
  };
}

const LiveMap: React.FC = () => {
  const [selectedBus, setSelectedBus] = useState<FleetBus | null>(null);
  const [selectedSchedule, setSelectedSchedule] = useState<any | null>(null);
  const [scheduleSearch, setScheduleSearch] = useState('');
  const [liveEta, setLiveEta] = useState<{ minutes: number; km: string; stopName: string } | null>(null);

  // 1. Fetch live fleet status from admin dashboard
  const { data, isLoading, refetch, isFetching } = useQuery<FleetBus[]>({
    queryKey: ['live-fleet'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<DashboardResponse>>('/admin/dashboard');
      return res.data.data?.fleetStatus || [];
    },
    refetchInterval: 5000, // Refresh every 5 seconds for live driver GPS updates
  });

  // 2. Fetch routes & all 139 Udupi Bus Stand schedules
  const { data: routesData } = useQuery<any[]>({
    queryKey: ['active-routes-stops'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<any[]>>('/routes?limit=100');
      return res.data.data || [];
    },
  });

  const { data: udupiSchedules } = useQuery<any[]>({
    queryKey: ['udupi-schedules'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<any[]>>('/routes/udupi-schedules');
      return res.data.data || [];
    },
  });

  // Extract stops for selected 139-schedule bus OR active fleet route
  const activeRouteStops: RouteStopInfo[] = React.useMemo(() => {
    if (selectedSchedule?.stops && selectedSchedule.stops.length > 0) {
      return selectedSchedule.stops.map((st: any) => ({
        id: `sched-${selectedSchedule.slNo}-${st.order}`,
        name: st.name,
        latitude: st.latitude,
        longitude: st.longitude,
        order: st.order,
        arrival: st.order === 1 ? selectedSchedule.departureTime : null,
      }));
    }
    if (!routesData || routesData.length === 0) return [];
    const route = routesData.find((r) => r.name?.includes('Udupi') || r.code === 'KA-20-UKS') || routesData[0];
    if (route?.stops) {
      return route.stops.map((rs: any) => ({
        id: rs.stop?.id || rs.id,
        name: rs.stop?.name || 'Stop',
        latitude: rs.stop?.latitude || 13.3409,
        longitude: rs.stop?.longitude || 74.7421,
        order: rs.stopOrder || 1,
        arrival: rs.scheduledArrivalTime,
        departure: rs.scheduledDepartureTime,
        distance: rs.distanceFromStart,
      }));
    }
    return [];
  }, [routesData, selectedSchedule]);

  const filteredSchedules = React.useMemo(() => {
    const list = udupiSchedules || [];
    if (!scheduleSearch.trim()) return list;
    const term = scheduleSearch.toLowerCase();
    return list.filter(
      (s) =>
        s.to.toLowerCase().includes(term) ||
        s.via.toLowerCase().includes(term) ||
        s.departureTime.includes(term) ||
        String(s.slNo) === term
    );
  }, [udupiSchedules, scheduleSearch]);

  // 3. Fetch active incidents
  const { data: incidents, refetch: refetchIncidents } = useQuery<IncidentItem[]>({
    queryKey: ['active-incidents'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<IncidentItem[]>>('/incidents?isResolved=false');
      return res.data.data || [];
    },
    refetchInterval: 10000,
  });

  const fleet = data || [];

  useEffect(() => {
    if (fleet.length > 0 && !selectedBus) {
      setSelectedBus(fleet[0]);
    } else if (selectedBus) {
      const refreshed = fleet.find((b) => b.tripId === selectedBus.tripId);
      if (refreshed) setSelectedBus(refreshed);
    }
  }, [fleet, selectedBus]);

  // Compute live road-snapped OSRM ETA to terminal stop (Shirva) when driver GPS is active
  useEffect(() => {
    const loc = selectedBus?.lastLocation;
    if (!loc) {
      setLiveEta(null);
      return;
    }
    const lastStop = activeRouteStops.length > 0
      ? [...activeRouteStops].sort((a, b) => a.order - b.order)[activeRouteStops.length - 1]
      : { name: 'Shirva Bus Stand', latitude: 13.2188, longitude: 74.8223 };

    let cancelled = false;
    fetch(`https://router.project-osrm.org/route/v1/driving/${loc.longitude},${loc.latitude};${lastStop.longitude},${lastStop.latitude}?overview=false`)
      .then((r) => r.json())
      .then((res) => {
        if (cancelled) return;
        const route = res?.routes?.[0];
        if (route) {
          setLiveEta({
            minutes: Math.max(1, Math.round((route.duration || 0) / 60)),
            km: ((route.distance || 0) / 1000).toFixed(1),
            stopName: lastStop.name,
          });
        }
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [selectedBus?.lastLocation?.latitude, selectedBus?.lastLocation?.longitude, activeRouteStops]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2">
            <span>Live Fleet Tracking & Route Corridor</span>
            <span className="text-xs font-semibold bg-primary-100 text-primary-800 px-2.5 py-0.5 rounded-full">
              Karnataka (KA-20)
            </span>
          </h2>
          <p className="text-sm text-gray-500">
            Real-time GPS telemetry from driver phones with fallback general route timetable for Udupi – Katpadi – Shirva
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => { refetch(); refetchIncidents(); }} loading={isFetching}>
          <RefreshCw size={14} className="mr-1.5" /> Refresh Telemetry
        </Button>
      </div>

      {/* Emergency Incidents & Delays Banner */}
      {incidents && incidents.length > 0 && (
        <div className="space-y-2">
          {incidents.map((inc) => (
            <div
              key={inc.id}
              className={`p-4 rounded-xl border flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 shadow-sm ${
                inc.severity === 'CRITICAL' || inc.severity === 'HIGH'
                  ? 'bg-red-50 border-red-300 text-red-900'
                  : 'bg-amber-50 border-amber-300 text-amber-900'
              }`}
            >
              <div className="flex items-center gap-3">
                <AlertTriangle className={inc.severity === 'CRITICAL' ? 'text-red-600 animate-bounce' : 'text-amber-600'} size={24} />
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-sm">
                      🚨 BUS {inc.trip?.bus?.busNumber || 'ALERT'} — {inc.severity} SEVERITY
                    </span>
                    {inc.delayMinutes > 0 && (
                      <span className="text-xs bg-red-200 text-red-800 font-bold px-2 py-0.5 rounded-full">
                        +{inc.delayMinutes} min delay
                      </span>
                    )}
                  </div>
                  <p className="text-xs mt-0.5 opacity-90">{inc.description}</p>
                </div>
              </div>

              <Button
                size="sm"
                variant="secondary"
                onClick={async () => {
                  try {
                    await apiClient.patch(`/incidents/${inc.id}/resolve`, { resolvedNote: 'Resolved by Admin' });
                    refetchIncidents();
                  } catch (e) {}
                }}
              >
                Mark Resolved
              </Button>
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Active Bus List Sidebar */}
        <div className="lg:col-span-1 space-y-3">
          <h3 className="text-sm font-semibold text-gray-700 uppercase tracking-wider flex items-center justify-between">
            <span>Fleet Vehicles ({fleet.length})</span>
            <span className="text-xs font-normal text-gray-500">Udupi Hub</span>
          </h3>

          {isLoading ? (
            <div className="p-8 text-center text-gray-400">Loading active fleet...</div>
          ) : fleet.length === 0 ? (
            <Card>
              <div className="text-center py-6">
                <Bus size={32} className="mx-auto text-gray-300 mb-2" />
                <p className="text-sm font-medium text-gray-700">No active trips</p>
                <p className="text-xs text-gray-400 mt-1">Start a trip to view live navigation on Udupi – Katpadi – Shirva.</p>
              </div>
            </Card>
          ) : (
            fleet.map((bus) => {
              const isSelected = selectedBus?.busId === bus.busId;
              const hasGps = bus.lastLocation != null;
              const isStale = bus.lastLocation?.isStale;

              return (
                <div
                  key={bus.tripId}
                  onClick={() => setSelectedBus(bus)}
                  className={`p-4 rounded-xl border transition-all cursor-pointer ${
                    isSelected
                      ? 'border-primary-500 bg-primary-50/70 shadow-sm'
                      : 'border-gray-200 bg-white hover:border-gray-300'
                  }`}
                >
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-gray-900">{bus.busNumber}</span>
                        <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded font-mono">
                          {bus.routeCode || 'KA-20-UKS'}
                        </span>
                      </div>
                      <p className="text-xs text-gray-600 mt-1 font-medium">{bus.routeName}</p>
                    </div>

                    {/* GPS Status Indicator */}
                    {hasGps && isStale ? (
                      <span className="flex items-center gap-1 text-[11px] font-semibold text-amber-700 bg-amber-100 px-2 py-0.5 rounded-full">
                        <AlertTriangle size={11} /> Stale GPS
                      </span>
                    ) : hasGps ? (
                      <span className="flex items-center gap-1 text-[11px] font-semibold text-green-700 bg-green-100 px-2 py-0.5 rounded-full">
                        <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" /> Live GPS
                      </span>
                    ) : (
                      <span className="flex items-center gap-1 text-[11px] font-semibold text-indigo-700 bg-indigo-50 border border-indigo-200 px-2 py-0.5 rounded-full" title="Driver phone GPS offline - showing general route corridor">
                        <Navigation size={10} /> General Route
                      </span>
                    )}
                  </div>

                  <div className="flex items-center justify-between text-xs text-gray-500 mt-3 pt-3 border-t border-gray-100">
                    <span className="flex items-center gap-1">
                      <Users size={12} className="text-primary-600" />
                      {bus.currentOnboard} / {bus.capacity} onboard
                    </span>
                    <span>Driver: {bus.driverName}</span>
                  </div>
                </div>
              );
            })
          )}

          {/* Udupi Bus Stand 139-Bus Timetable & Route Selector */}
          <div className="pt-4 mt-4 border-t border-gray-200 space-y-2.5">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold text-gray-700 uppercase tracking-wider">
                Udupi 139 Bus Schedules
              </h3>
              {selectedSchedule && (
                <button
                  onClick={() => setSelectedSchedule(null)}
                  className="text-xs text-sky-600 font-semibold hover:underline"
                >
                  Clear Selection
                </button>
              )}
            </div>
            <input
              type="text"
              value={scheduleSearch}
              onChange={(e) => setScheduleSearch(e.target.value)}
              placeholder="Search To, Via, Time (e.g. Karkala, 07.00)..."
              className="w-full px-3 py-2 text-xs border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-sky-500"
            />
            <div className="max-h-72 overflow-y-auto space-y-1.5 pr-1">
              {filteredSchedules.map((sch) => {
                const isSel = selectedSchedule?.slNo === sch.slNo;
                return (
                  <div
                    key={sch.slNo}
                    onClick={() => setSelectedSchedule(sch)}
                    className={`p-2.5 rounded-lg border text-xs cursor-pointer transition ${
                      isSel
                        ? 'bg-sky-50 border-sky-500 shadow-sm'
                        : 'bg-white border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <div className="flex items-center justify-between font-bold text-gray-900">
                      <span>#{sch.slNo} {sch.from} → {sch.to}</span>
                      <span className="text-sky-700 bg-sky-100 px-1.5 py-0.5 rounded font-mono">
                        {sch.departureTime}
                      </span>
                    </div>
                    <p className="text-[11px] text-gray-500 mt-0.5 truncate">
                      Via: {sch.via} • {sch.serviceClass}
                    </p>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Live Map Display Container */}
        <div className="lg:col-span-2">
          <Card className="h-full flex flex-col min-h-[560px]">
            {/* Header details */}
            <div className="flex flex-wrap items-center justify-between gap-2 pb-4 mb-4 border-b border-gray-100">
              <div>
                <h3 className="font-bold text-gray-900 text-lg">
                  {selectedSchedule
                    ? `#${selectedSchedule.slNo} Udupi → ${selectedSchedule.to} (Dep ${selectedSchedule.departureTime})`
                    : selectedBus
                    ? `Bus ${selectedBus.busNumber} — ${selectedBus.routeName}`
                    : 'Udupi – Katpadi – Shirva Corridor'}
                </h3>
                <p className="text-xs text-gray-500">
                  {selectedSchedule
                    ? `Service: ${selectedSchedule.serviceClass} | Via: ${selectedSchedule.via}`
                    : selectedBus
                    ? `Driver: ${selectedBus.driverName} | Conductor: ${selectedBus.conductorName}`
                    : 'Displaying real-time vehicle positions & scheduled stops'}
                </p>
              </div>
              {selectedBus && (
                <div className="flex items-center gap-3">
                  <div className="text-right">
                    <p className="text-xs text-gray-400">Onboard Passengers</p>
                    <p className="font-bold text-primary-600 text-base">
                      {selectedBus.currentOnboard} / {selectedBus.capacity}
                    </p>
                  </div>
                </div>
              )}
            </div>

            {/* General Route Mode Banner when Driver does not share GPS */}
            {selectedBus && !selectedBus.lastLocation && (
              <div className="mb-4 p-3 bg-indigo-50 border border-indigo-200 rounded-lg flex items-start gap-2.5 text-indigo-950 text-xs">
                <Info size={16} className="text-indigo-600 flex-shrink-0 mt-0.5" />
                <div>
                  <p className="font-bold text-indigo-900">📍 General Route Mode (Driver GPS Offline)</p>
                  <p className="mt-0.5 text-indigo-700 leading-relaxed">
                    Driver phone location is not active. Showing standard route corridor and timetable stops across <strong>Udupi ➔ Kinnimulki ➔ Katpadi ➔ Shankarapura ➔ Shirva</strong> without live GPS navigation.
                  </p>
                </div>
              </div>
            )}

            {/* Stale GPS Warning Banner */}
            {selectedBus?.lastLocation?.isStale && (
              <div className="mb-4 p-3 bg-amber-50 border border-amber-200 rounded-lg flex items-center gap-2.5 text-amber-800 text-xs">
                <AlertTriangle size={16} className="text-amber-600 flex-shrink-0" />
                <span>
                  <strong>Warning:</strong> GPS location was last updated {selectedBus.lastLocation.ageSeconds} seconds ago ({'>'} 5 minutes). Telemetry may not reflect real-time position.
                </span>
              </div>
            )}

            {/* Interactive Leaflet Map Canvas */}
            <div className="flex-1 flex flex-col gap-4 min-h-[440px]">
              <LeafletFleetMap
                fleet={fleet}
                selectedBus={selectedBus}
                onSelectBus={(b) => setSelectedBus(b)}
                routeStops={activeRouteStops}
              />

              {/* Telemetry info bar (Live GPS active) */}
              {selectedBus?.lastLocation ? (
                <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 bg-emerald-50/60 border border-emerald-200 p-3 rounded-lg text-xs">
                  <div>
                    <span className="text-gray-500">Live Speed:</span>
                    <p className="font-bold text-gray-900">{selectedBus.lastLocation.speed?.toFixed(1) || 0} km/h</p>
                  </div>
                  <div>
                    <span className="text-emerald-700 font-medium">OSRM Road ETA:</span>
                    <p className="font-bold text-emerald-900">
                      {liveEta ? `${liveEta.minutes} min (${liveEta.km} km)` : 'Calculating...'}
                    </p>
                  </div>
                  <div>
                    <span className="text-gray-500">Heading:</span>
                    <p className="font-bold text-gray-900 flex items-center gap-1">
                      <Compass size={12} /> {selectedBus.lastLocation.heading?.toFixed(0) || 0}°
                    </p>
                  </div>
                  <div>
                    <span className="text-gray-500">Coordinates:</span>
                    <p className="font-mono text-gray-800">
                      {selectedBus.lastLocation.latitude.toFixed(4)}, {selectedBus.lastLocation.longitude.toFixed(4)}
                    </p>
                  </div>
                  <div>
                    <span className="text-gray-500">Last Telemetry:</span>
                    <p className="text-gray-800">
                      {new Date(selectedBus.lastLocation.timestamp).toLocaleTimeString()}
                    </p>
                  </div>
                </div>
              ) : selectedBus ? (
                /* Timetable info bar (General route fallback mode) */
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-indigo-50/60 border border-indigo-200 p-3 rounded-lg text-xs">
                  <div>
                    <span className="text-indigo-600 font-medium">Tracking Mode:</span>
                    <p className="font-bold text-indigo-950">Timetable Schedule</p>
                  </div>
                  <div>
                    <span className="text-indigo-600 font-medium">Corridor Length:</span>
                    <p className="font-bold text-indigo-950">18.5 km (5 Stops)</p>
                  </div>
                  <div>
                    <span className="text-indigo-600 font-medium">Corridor:</span>
                    <p className="font-medium text-indigo-950">Udupi ➔ Katpadi ➔ Shirva</p>
                  </div>
                  <div>
                    <span className="text-indigo-600 font-medium">Driver Phone:</span>
                    <p className="text-indigo-900 font-semibold">GPS Inactive</p>
                  </div>
                </div>
              ) : null}
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default LiveMap;
