import React, { useState } from 'react';
import { Settings as SettingsIcon, Save, Key, Database, Shield, Globe } from 'lucide-react';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';

const Settings: React.FC = () => {
  const [apiKey, setApiKey] = useState('AIzaSyD-sample-maps-key-placeholder');
  const [refreshInterval, setRefreshInterval] = useState('15');
  const [autoTracking, setAutoTracking] = useState(true);
  const [saved, setSaved] = useState(false);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-gray-900">System & Admin Settings</h2>
        <p className="text-sm text-gray-500">Configure telemetry thresholds, API keys, and platform preferences</p>
      </div>

      {saved && (
        <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-xs font-semibold text-green-800">
          Settings updated and saved successfully!
        </div>
      )}

      <form onSubmit={handleSave} className="space-y-6">
        <Card>
          <div className="flex items-center gap-2 mb-4 pb-3 border-b border-gray-100">
            <Globe size={18} className="text-primary-600" />
            <h3 className="text-sm font-bold text-gray-900">Map & Geolocation Configuration</h3>
          </div>
          <div className="space-y-4 max-w-xl">
            <Input
              label="Google Maps / Mapbox Key (Optional for Custom Overlays)"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              placeholder="Enter Maps API Key"
              hint="Default map runs seamlessly on OpenStreetMap with zero setup."
            />
            <Input
              label="Live Telemetry Poll Frequency (Seconds)"
              type="number"
              value={refreshInterval}
              onChange={(e) => setRefreshInterval(e.target.value)}
              hint="Determines how frequently active fleet positions update on dashboard."
            />
          </div>
        </Card>

        <Card>
          <div className="flex items-center gap-2 mb-4 pb-3 border-b border-gray-100">
            <Shield size={18} className="text-primary-600" />
            <h3 className="text-sm font-bold text-gray-900">Security & Access Policies</h3>
          </div>
          <div className="space-y-3">
            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={autoTracking}
                onChange={(e) => setAutoTracking(e.target.checked)}
                className="w-4 h-4 text-primary-600 rounded border-gray-300 focus:ring-primary-500"
              />
              <span className="text-sm font-medium text-gray-700">
                Enforce Stale GPS Highlighting for vehicles with no ping for over 5 minutes
              </span>
            </label>
          </div>
        </Card>

        <div className="flex justify-end">
          <Button type="submit" variant="primary">
            <Save size={16} className="mr-1.5" /> Save Configuration
          </Button>
        </div>
      </form>
    </div>
  );
};

export default Settings;
