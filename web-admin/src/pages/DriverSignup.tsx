import React, { useState } from 'react';
import { Bus, CheckCircle2, Briefcase } from 'lucide-react';
import { apiClient } from '../lib/axios';
import Button from '../components/ui/Button';
import Card from '../components/ui/Card';
import Input from '../components/ui/Input';

const DriverSignup: React.FC = () => {
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState<'DRIVER' | 'CONDUCTOR'>('DRIVER');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [licenseNumber, setLicenseNumber] = useState('');
  const [badgeNumber, setBadgeNumber] = useState('');
  const [experienceYears, setExperienceYears] = useState(3);
  const [preferredRoutes, setPreferredRoutes] = useState('Udupi – Katpadi – Shirva');
  const [expectedSalary, setExpectedSalary] = useState(23000);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await apiClient.post('/jobs/apply', {
        fullName,
        role,
        phone,
        email,
        licenseNumber,
        badgeNumber: badgeNumber || undefined,
        experienceYears,
        preferredRoutes,
        expectedSalary,
      });
      setSubmitted(true);
    } catch (err: any) {
      setError(err.response?.data?.error?.message || 'Failed to submit application.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center p-4">
      <div className="max-w-lg w-full">
        <div className="flex items-center justify-center gap-3 mb-6">
          <div className="w-12 h-12 rounded-2xl bg-sky-600 text-white flex items-center justify-center shadow-lg">
            <Bus className="w-7 h-7" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-slate-900">SmartBus Careers</h1>
            <p className="text-sm text-slate-500">Udupi – Katpadi – Shirva Bus Fleet Recruitment</p>
          </div>
        </div>

        <Card className="p-6 shadow-xl border border-slate-200 bg-white">
          {submitted ? (
            <div className="text-center py-8 space-y-3">
              <CheckCircle2 className="w-14 h-14 text-emerald-600 mx-auto" />
              <h2 className="text-xl font-bold text-slate-900">Application Submitted!</h2>
              <p className="text-sm text-slate-600">
                Your profile is now visible on the Bus Owner Recruitment Portal. Once hired, you can log in directly using the SmartBus Android App.
              </p>
              <Button onClick={() => setSubmitted(false)} variant="secondary" className="mt-4">
                Submit Another Application
              </Button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="flex items-center gap-2 pb-2 border-b border-slate-100">
                <Briefcase className="w-5 h-5 text-sky-600" />
                <h2 className="text-lg font-bold text-slate-800">Apply as Driver or Conductor</h2>
              </div>

              {error && (
                <div className="p-3 rounded-lg bg-red-50 text-red-700 text-sm border border-red-200">
                  {error}
                </div>
              )}

              <div className="flex gap-2">
                {(['DRIVER', 'CONDUCTOR'] as const).map((r) => (
                  <button
                    key={r}
                    type="button"
                    onClick={() => setRole(r)}
                    className={`flex-1 py-2 px-3 rounded-lg text-sm font-semibold border transition ${
                      role === r
                        ? 'bg-sky-600 text-white border-sky-600'
                        : 'bg-white text-slate-600 border-slate-300'
                    }`}
                  >
                    {r === 'DRIVER' ? 'Bus Driver (HMV)' : 'Bus Conductor'}
                  </button>
                ))}
              </div>

              <Input label="Full Name" value={fullName} onChange={(e) => setFullName(e.target.value)} required placeholder="e.g. Ramesh Poojary" />
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <Input label="Mobile Number" value={phone} onChange={(e) => setPhone(e.target.value)} required placeholder="+91 98451 22345" />
                <Input label="Email Address" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required placeholder="ramesh@example.com" />
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <Input label="HMV License / Conductor No." value={licenseNumber} onChange={(e) => setLicenseNumber(e.target.value)} required placeholder="KA-20-2016-0045892" />
                <Input label="Badge No. (Optional)" value={badgeNumber} onChange={(e) => setBadgeNumber(e.target.value)} placeholder="BDG-KA20-891" />
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <Input label="Experience (Years)" type="number" value={experienceYears} onChange={(e) => setExperienceYears(Number(e.target.value))} />
                <Input label="Expected Salary (₹/mo)" type="number" value={expectedSalary} onChange={(e) => setExpectedSalary(Number(e.target.value))} />
              </div>
              <Input label="Preferred Route" value={preferredRoutes} onChange={(e) => setPreferredRoutes(e.target.value)} />

              <Button type="submit" disabled={submitting} className="w-full py-2.5">
                {submitting ? 'Submitting...' : 'Submit Job Application'}
              </Button>
            </form>
          )}
        </Card>
      </div>
    </div>
  );
};

export default DriverSignup;
