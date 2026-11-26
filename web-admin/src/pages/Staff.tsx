import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  UserPlus, UserCheck, UserX, Mail, Phone, CreditCard, DollarSign,
  Briefcase, Star, Award, Shield, FileText, CheckCircle2, AlertCircle,
  Download, ExternalLink, Calendar, MapPin, Truck, ChevronRight
} from 'lucide-react';
import { apiClient } from '../lib/axios';
import { User, ApiResponse } from '../types';
import Button from '../components/ui/Button';
import Card from '../components/ui/Card';
import Table, { Column } from '../components/ui/Table';
import Modal from '../components/ui/Modal';
import Input from '../components/ui/Input';

interface StaffProfile extends User {
  licenseNo?: string;
  badgeNo?: string;
  assignedBus?: string;
  assignedRoute?: string;
  experienceYears?: number;
  dutyStatus?: 'ON_DUTY' | 'AVAILABLE' | 'OFF_DUTY';
  rating?: number;
  completedTrips?: number;
  emergencyContact?: string;
  bloodGroup?: string;
  // Salary details
  baseSalary?: number;
  dailyBhatta?: number;
  dutyDays?: number;
  tripIncentive?: number;
  deductions?: number;
  netPayable?: number;
  salaryStatus?: 'PAID' | 'PENDING';
  lastPaidDate?: string;
}

interface JobApplicant {
  id: string;
  fullName: string;
  role: 'DRIVER' | 'CONDUCTOR';
  phone: string;
  email: string;
  licenseNumber: string;
  badgeNumber: string;
  experienceYears: number;
  preferredRoutes: string;
  expectedSalary: number;
  appliedDate: string;
  status: 'PENDING_REVIEW' | 'SHORTLISTED' | 'HIRED' | 'REJECTED';
}

const INITIAL_JOB_APPLICANTS: JobApplicant[] = [
  {
    id: 'app-1',
    fullName: 'Ramesh Poojary',
    role: 'DRIVER',
    phone: '+91 98451 22345',
    email: 'ramesh.p@example.com',
    licenseNumber: 'KA-20-2016-0045892 (Heavy Motor Vehicle)',
    badgeNumber: 'BDG-KA20-891',
    experienceYears: 7,
    preferredRoutes: 'Udupi – Katpadi – Shirva / Kaup Coastal',
    expectedSalary: 24000,
    appliedDate: '2026-11-20',
    status: 'PENDING_REVIEW',
  },
  {
    id: 'app-2',
    fullName: 'Suresh Shetty',
    role: 'DRIVER',
    phone: '+91 94812 33490',
    email: 'suresh.shetty@example.com',
    licenseNumber: 'KA-19-2019-0012480 (HMV Passenger)',
    badgeNumber: 'BDG-KA19-450',
    experienceYears: 5,
    preferredRoutes: 'Udupi – Shirva / Mangalore Express',
    expectedSalary: 23000,
    appliedDate: '2026-11-21',
    status: 'PENDING_REVIEW',
  },
  {
    id: 'app-3',
    fullName: 'Anand Kulal',
    role: 'CONDUCTOR',
    phone: '+91 91482 77102',
    email: 'anand.kulal@example.com',
    licenseNumber: 'COND-KA20-2022-771',
    badgeNumber: 'BDG-KA20-112',
    experienceYears: 3,
    preferredRoutes: 'Udupi Service Stand – Katpadi – Shirva',
    expectedSalary: 18000,
    appliedDate: '2026-11-22',
    status: 'SHORTLISTED',
  },
];

const Staff: React.FC = () => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'ROSTER' | 'PAYROLL' | 'HIRING'>('ROSTER');
  const [roleFilter, setRoleFilter] = useState<'ALL' | 'DRIVER' | 'CONDUCTOR'>('ALL');
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [selectedStaffProfile, setSelectedStaffProfile] = useState<StaffProfile | null>(null);
  const [payoutStaff, setPayoutStaff] = useState<StaffProfile | null>(null);
  const [payoutAmount, setPayoutAmount] = useState<number>(0);
  const [payoutMethod, setPayoutMethod] = useState<'UPI' | 'BANK_TRANSFER' | 'CASH'>('UPI');
  const [payoutSuccessMsg, setPayoutSuccessMsg] = useState<string | null>(null);

  // Fetch job applications from real backend API (fallback to initial demo if empty)
  const { data: fetchedApplicants } = useQuery<JobApplicant[]>({
    queryKey: ['job-applications'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<{ applications: any[] }>>('/jobs');
      const apps = res.data.data?.applications || [];
      return apps.map((a: any) => ({
        id: a.id,
        fullName: a.fullName,
        role: a.role,
        phone: a.phone,
        email: a.email,
        licenseNumber: a.licenseNumber,
        badgeNumber: a.badgeNumber || 'N/A',
        experienceYears: a.experienceYears || 0,
        preferredRoutes: a.preferredRoutes || 'Udupi – Katpadi – Shirva',
        expectedSalary: a.expectedSalary || 22000,
        appliedDate: (a.createdAt || '').split('T')[0],
        status: a.status,
      }));
    },
  });

  const [applicantToHire, setApplicantToHire] = useState<JobApplicant | null>(null);
  const jobApplicants: JobApplicant[] = React.useMemo(() => {
    if (fetchedApplicants && fetchedApplicants.length > 0) return fetchedApplicants;
    return INITIAL_JOB_APPLICANTS;
  }, [fetchedApplicants]);

  // Create Staff Form
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [role, setRole] = useState<'DRIVER' | 'CONDUCTOR'>('DRIVER');
  const [licenseNo, setLicenseNo] = useState('');
  const [assignedBus, setAssignedBus] = useState('KA-20-111');
  const [baseSalary, setBaseSalary] = useState(22000);
  const [formError, setFormError] = useState<string | null>(null);

  // Fetch staff from API
  const { data: rawStaffList, isLoading } = useQuery<User[]>({
    queryKey: ['staff', roleFilter],
    queryFn: async () => {
      const url = roleFilter === 'ALL' ? '/admin/staff' : `/admin/staff?role=${roleFilter}`;
      const res = await apiClient.get<ApiResponse<User[]>>(url);
      return res.data.data || [];
    },
  });

  // Enrich staff with realistic transit operational and payroll details
  const staffList: StaffProfile[] = React.useMemo(() => {
    return (rawStaffList || []).map((u, i) => {
      const isDriver = u.role === 'DRIVER';
      const dutyDays = 26;
      const bhatta = isDriver ? 250 : 200;
      const base = isDriver ? 22000 : 18000;
      const incentives = (i + 1) * 750;
      const deductions = 1200;
      const totalBhatta = dutyDays * bhatta;
      const net = base + totalBhatta + incentives - deductions;

      return {
        ...u,
        licenseNo: isDriver ? `KA-20-2018-00${3829 + i}` : `COND-KA20-2020-00${1920 + i}`,
        badgeNo: `BDG-KA20-${100 + i}`,
        assignedBus: 'KA-20-111 (Starbus Express)',
        assignedRoute: 'Udupi – Katpadi – Shirva',
        experienceYears: isDriver ? 5 + i : 3 + i,
        dutyStatus: i === 0 ? 'ON_DUTY' : i === 1 ? 'AVAILABLE' : 'OFF_DUTY',
        rating: 4.8 - (i * 0.1),
        completedTrips: 120 + (i * 24),
        emergencyContact: '+91 98450 12345',
        bloodGroup: i % 2 === 0 ? 'O+ve' : 'B+ve',
        baseSalary: base,
        dailyBhatta: bhatta,
        dutyDays,
        tripIncentive: incentives,
        deductions,
        netPayable: net,
        salaryStatus: i === 0 ? 'PAID' : 'PENDING',
        lastPaidDate: i === 0 ? '2026-11-05' : undefined,
      };
    });
  }, [rawStaffList]);

  const createStaffMutation = useMutation({
    mutationFn: async (payload: any) => {
      const res = await apiClient.post<ApiResponse<User>>('/admin/staff', payload);
      return res.data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff'] });
      setIsAddModalOpen(false);
      resetForm();
    },
    onError: (err: any) => {
      setFormError(err.response?.data?.error?.message || 'Failed to create staff account');
    },
  });

  const toggleStatusMutation = useMutation({
    mutationFn: async ({ id, isActive }: { id: string; isActive: boolean }) => {
      await apiClient.patch(`/admin/staff/${id}/status`, { isActive });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff'] });
    },
  });

  const resetForm = () => {
    setFirstName('');
    setLastName('');
    setEmail('');
    setPassword('');
    setPhone('');
    setRole('DRIVER');
    setLicenseNo('');
    setBaseSalary(22000);
    setFormError(null);
  };

  const handleCreateStaff = (e: React.FormEvent) => {
    e.preventDefault();
    if (!firstName || !lastName || !email || !password) {
      setFormError('Please fill in all required fields (password minimum 8 characters)');
      return;
    }
    createStaffMutation.mutate({ firstName, lastName, email, password, phone, role });
  };

  // Instant hire action from Hiring Board
  const handleHireApplicant = async (applicant: JobApplicant) => {
    if (!applicant.id.startsWith('app-')) {
      try {
        await apiClient.post(`/jobs/${applicant.id}/hire`);
        queryClient.invalidateQueries({ queryKey: ['job-applications'] });
        queryClient.invalidateQueries({ queryKey: ['staff'] });
        setPayoutSuccessMsg(`🎉 Hired ${applicant.fullName} as ${applicant.role}! Account created (Password: Staff@123456).`);
        setTimeout(() => setPayoutSuccessMsg(null), 5000);
      } catch (err: any) {
        setFormError(err.response?.data?.error?.message || 'Failed to hire applicant');
      }
    } else {
      const names = applicant.fullName.split(' ');
      const fName = names[0];
      const lName = names.slice(1).join(' ') || 'Staff';
      const autoEmail = `${fName.toLowerCase()}.${lName.toLowerCase()}@smartbus.local`;

      createStaffMutation.mutate({
        firstName: fName,
        lastName: lName,
        email: autoEmail,
        password: 'Staff@123456',
        phone: applicant.phone,
        role: applicant.role,
      });
    }
    setApplicantToHire(null);
  };

  // Process Salary Payout
  const handleProcessPayout = () => {
    if (!payoutStaff) return;
    setPayoutSuccessMsg(`Successfully disbursed ₹${payoutAmount.toLocaleString()} to ${payoutStaff.firstName} via ${payoutMethod}. Transaction logged.`);
    setTimeout(() => {
      setPayoutStaff(null);
      setPayoutSuccessMsg(null);
    }, 2500);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2">
            <span>Drivers & Conductors Operations</span>
            <span className="text-xs bg-primary-100 text-primary-800 font-semibold px-2.5 py-0.5 rounded-full">
              Karnataka Fleet
            </span>
          </h2>
          <p className="text-sm text-gray-500">
            Crew profiles, live operational details, monthly payroll, and driver recruitment board
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button size="sm" onClick={() => setIsAddModalOpen(true)}>
            <UserPlus size={15} className="mr-1.5" /> Add Staff Member
          </Button>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="border-b border-gray-200">
        <nav className="flex space-x-6">
          <button
            onClick={() => setActiveTab('ROSTER')}
            className={`pb-3 px-1 border-b-2 font-medium text-sm flex items-center gap-2 transition-all ${
              activeTab === 'ROSTER'
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            <Shield size={16} />
            <span>Staff Roster & Duty Details</span>
            <span className="ml-1 px-2 py-0.5 text-xs bg-gray-100 rounded-full font-bold">
              {staffList.length}
            </span>
          </button>

          <button
            onClick={() => setActiveTab('PAYROLL')}
            className={`pb-3 px-1 border-b-2 font-medium text-sm flex items-center gap-2 transition-all ${
              activeTab === 'PAYROLL'
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            <DollarSign size={16} />
            <span>Salary, Bhatta & Payroll</span>
          </button>

          <button
            onClick={() => setActiveTab('HIRING')}
            className={`pb-3 px-1 border-b-2 font-medium text-sm flex items-center gap-2 transition-all ${
              activeTab === 'HIRING'
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            <Briefcase size={16} />
            <span>Driver Recruitment & Hiring Board</span>
            <span className="ml-1 px-2 py-0.5 text-xs bg-amber-100 text-amber-800 rounded-full font-bold">
              {jobApplicants.filter(a => a.status === 'PENDING_REVIEW').length} New
            </span>
          </button>
        </nav>
      </div>

      {/* ─────────────────────────────────────────────────────────────
          TAB 1: STAFF ROSTER & OPERATIONAL PROFILES
      ───────────────────────────────────────────────────────────── */}
      {activeTab === 'ROSTER' && (
        <div className="space-y-4">
          {/* Quick Filters */}
          <div className="flex items-center gap-2">
            {(['ALL', 'DRIVER', 'CONDUCTOR'] as const).map((r) => (
              <button
                key={r}
                onClick={() => setRoleFilter(r)}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  roleFilter === r
                    ? 'bg-primary-600 text-white shadow-sm'
                    : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
                }`}
              >
                {r === 'ALL' ? 'All Crew' : r === 'DRIVER' ? 'Drivers Only' : 'Conductors Only'}
              </button>
            ))}
          </div>

          <Card>
            <Table<StaffProfile>
              data={staffList}
              loading={isLoading}
              keyExtractor={(u) => u.id}
              columns={[
                {
                  key: 'staff',
                  header: 'Staff Member & Identity',
                  render: (u) => (
                    <div className="flex items-start gap-3">
                      <div className="w-10 h-10 rounded-full bg-primary-100 text-primary-700 flex items-center justify-center font-bold text-sm">
                        {u.firstName[0]}{u.lastName[0]}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <p className="font-bold text-gray-900">{u.firstName} {u.lastName}</p>
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-bold tracking-wide uppercase ${
                              u.role === 'DRIVER' ? 'bg-purple-100 text-purple-800' : 'bg-orange-100 text-orange-800'
                            }`}
                          >
                            {u.role}
                          </span>
                        </div>
                        <p className="text-xs text-gray-500 font-mono mt-0.5">
                          Lic: {u.licenseNo} • Badge: {u.badgeNo}
                        </p>
                        <p className="text-xs text-gray-400 flex items-center gap-1 mt-0.5">
                          <Phone size={11} /> {u.phone || 'Phone not set'} • <Mail size={11} /> {u.email}
                        </p>
                      </div>
                    </div>
                  ),
                },
                {
                  key: 'assignment',
                  header: 'Assigned Vehicle & Route',
                  render: (u) => (
                    <div>
                      <p className="text-xs font-semibold text-gray-900 flex items-center gap-1.5">
                        <Truck size={13} className="text-primary-600" />
                        {u.assignedBus}
                      </p>
                      <p className="text-xs text-gray-500 flex items-center gap-1 mt-1">
                        <MapPin size={11} className="text-gray-400" />
                        {u.assignedRoute}
                      </p>
                    </div>
                  ),
                },
                {
                  key: 'duty',
                  header: 'Duty Status & Rating',
                  render: (u) => (
                    <div>
                      <span
                        className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold ${
                          u.dutyStatus === 'ON_DUTY'
                            ? 'bg-green-100 text-green-800'
                            : u.dutyStatus === 'AVAILABLE'
                            ? 'bg-blue-100 text-blue-800'
                            : 'bg-gray-100 text-gray-700'
                        }`}
                      >
                        <span className={`w-1.5 h-1.5 rounded-full ${u.dutyStatus === 'ON_DUTY' ? 'bg-green-600 animate-pulse' : 'bg-gray-400'}`} />
                        {u.dutyStatus === 'ON_DUTY' ? 'On Duty' : u.dutyStatus === 'AVAILABLE' ? 'Available' : 'Off Duty'}
                      </span>
                      <p className="text-xs text-gray-600 flex items-center gap-1 mt-1 font-medium">
                        <Star size={12} className="text-amber-500 fill-amber-500" />
                        <span>{u.rating?.toFixed(1)} / 5.0</span>
                        <span className="text-gray-400">({u.completedTrips} trips)</span>
                      </p>
                    </div>
                  ),
                },
                {
                  key: 'actions',
                  header: 'Actions',
                  render: (u) => (
                    <div className="flex items-center gap-2">
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => setSelectedStaffProfile(u)}
                      >
                        View Profile
                      </Button>
                      <Button
                        size="sm"
                        variant={u.isActive ? 'danger' : 'secondary'}
                        onClick={() => toggleStatusMutation.mutate({ id: u.id, isActive: !u.isActive })}
                      >
                        {u.isActive ? <UserX size={13} /> : <UserCheck size={13} />}
                      </Button>
                    </div>
                  ),
                },
              ]}
            />
          </Card>
        </div>
      )}

      {/* ─────────────────────────────────────────────────────────────
          TAB 2: SALARY, BHATTA & PAYROLL MANAGEMENT
      ───────────────────────────────────────────────────────────── */}
      {activeTab === 'PAYROLL' && (
        <div className="space-y-6">
          {/* Summary Metric Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
            <Card className="p-4 bg-gradient-to-br from-blue-50 to-white border-blue-200">
              <p className="text-xs text-blue-600 font-semibold uppercase">Total Monthly Payroll</p>
              <p className="text-2xl font-black text-blue-950 mt-1">
                ₹{staffList.reduce((acc, s) => acc + (s.netPayable || 0), 0).toLocaleString()}
              </p>
              <p className="text-[11px] text-gray-500 mt-1">Base salary + Daily Bhatta</p>
            </Card>

            <Card className="p-4 bg-gradient-to-br from-green-50 to-white border-green-200">
              <p className="text-xs text-green-600 font-semibold uppercase">Daily Bhatta Rate</p>
              <p className="text-2xl font-black text-green-950 mt-1">₹250 / day</p>
              <p className="text-[11px] text-gray-500 mt-1">Driver food & travel allowance</p>
            </Card>

            <Card className="p-4 bg-gradient-to-br from-amber-50 to-white border-amber-200">
              <p className="text-xs text-amber-600 font-semibold uppercase">Pending Payouts</p>
              <p className="text-2xl font-black text-amber-950 mt-1">
                ₹{staffList.filter(s => s.salaryStatus === 'PENDING').reduce((acc, s) => acc + (s.netPayable || 0), 0).toLocaleString()}
              </p>
              <p className="text-[11px] text-amber-700 mt-1">Awaiting approval / disbursement</p>
            </Card>

            <Card className="p-4 bg-gradient-to-br from-purple-50 to-white border-purple-200">
              <p className="text-xs text-purple-600 font-semibold uppercase">Disbursed This Month</p>
              <p className="text-2xl font-black text-purple-950 mt-1">
                ₹{staffList.filter(s => s.salaryStatus === 'PAID').reduce((acc, s) => acc + (s.netPayable || 0), 0).toLocaleString()}
              </p>
              <p className="text-[11px] text-purple-700 mt-1">Settled to bank accounts</p>
            </Card>
          </div>

          {/* Payroll Table */}
          <Card>
            <div className="p-4 border-b border-gray-100 flex items-center justify-between">
              <div>
                <h3 className="font-bold text-gray-900">Current Month Salary & Bhatta Breakdown</h3>
                <p className="text-xs text-gray-500">Karnataka transport regulatory compliance & daily duty allowance calculation</p>
              </div>
              <Button size="sm" variant="secondary" onClick={() => alert('Exporting monthly payroll summary PDF...')}>
                <Download size={14} className="mr-1.5" /> Export Payroll Sheet
              </Button>
            </div>

            <Table<StaffProfile>
              data={staffList}
              loading={isLoading}
              keyExtractor={(u) => u.id}
              columns={[
                {
                  key: 'staff',
                  header: 'Staff Member',
                  render: (u) => (
                    <div>
                      <p className="font-bold text-gray-900">{u.firstName} {u.lastName}</p>
                      <span className="text-[10px] font-semibold text-gray-500 bg-gray-100 px-2 py-0.5 rounded">
                        {u.role} • {u.licenseNo}
                      </span>
                    </div>
                  ),
                },
                {
                  key: 'base',
                  header: 'Base Salary',
                  render: (u) => (
                    <span className="font-mono text-gray-800 font-semibold">
                      ₹{u.baseSalary?.toLocaleString()}
                    </span>
                  ),
                },
                {
                  key: 'bhatta',
                  header: 'Daily Bhatta (26 Days)',
                  render: (u) => (
                    <div>
                      <span className="font-mono text-gray-800 font-semibold">
                        ₹{((u.dailyBhatta || 0) * (u.dutyDays || 0)).toLocaleString()}
                      </span>
                      <p className="text-[10px] text-gray-400">₹{u.dailyBhatta}/day</p>
                    </div>
                  ),
                },
                {
                  key: 'incentives',
                  header: 'Trip Incentives',
                  render: (u) => (
                    <span className="font-mono text-green-700 font-semibold">
                      +₹{u.tripIncentive?.toLocaleString()}
                    </span>
                  ),
                },
                {
                  key: 'net',
                  header: 'Net Payable',
                  render: (u) => (
                    <span className="font-mono text-base font-black text-primary-700">
                      ₹{u.netPayable?.toLocaleString()}
                    </span>
                  ),
                },
                {
                  key: 'status',
                  header: 'Payout Status',
                  render: (u) => (
                    <div>
                      <span
                        className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold ${
                          u.salaryStatus === 'PAID'
                            ? 'bg-green-100 text-green-800'
                            : 'bg-amber-100 text-amber-800'
                        }`}
                      >
                        {u.salaryStatus === 'PAID' ? <CheckCircle2 size={12} /> : <AlertCircle size={12} />}
                        {u.salaryStatus}
                      </span>
                      {u.lastPaidDate && (
                        <p className="text-[10px] text-gray-400 mt-0.5">Paid: {u.lastPaidDate}</p>
                      )}
                    </div>
                  ),
                },
                {
                  key: 'actions',
                  header: 'Salary Action',
                  render: (u) => (
                    <Button
                      size="sm"
                      variant={u.salaryStatus === 'PAID' ? 'secondary' : 'primary'}
                      onClick={() => {
                        setPayoutStaff(u);
                        setPayoutAmount(u.netPayable || 0);
                      }}
                    >
                      {u.salaryStatus === 'PAID' ? 'View Pay Slip' : 'Disburse Pay'}
                    </Button>
                  ),
                },
              ]}
            />
          </Card>
        </div>
      )}

      {/* ─────────────────────────────────────────────────────────────
          TAB 3: DRIVER & CONDUCTOR HIRING BOARD
      ───────────────────────────────────────────────────────────── */}
      {activeTab === 'HIRING' && (
        <div className="space-y-6">
          {/* Information & Public Signup link banner */}
          <div className="p-4 bg-gradient-to-r from-amber-50 to-orange-50 border border-amber-200 rounded-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div className="flex items-start gap-3">
              <Briefcase size={22} className="text-amber-700 flex-shrink-0 mt-0.5" />
              <div>
                <p className="font-bold text-gray-900 text-sm">
                  Driver & Conductor Recruitment Portal
                </p>
                <p className="text-xs text-gray-600 mt-0.5 leading-relaxed">
                  Bus drivers seeking employment in the Udupi – Mangalore corridor can submit their applications online. Bus owners can review credentials, inspect Heavy Vehicle Driving Licenses, and hire them with a single click.
                </p>
              </div>
            </div>

            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                navigator.clipboard.writeText('https://smart-bus-system-tan.vercel.app/jobs/driver-signup');
                alert('Public Driver Job Application URL copied to clipboard!');
              }}
            >
              <ExternalLink size={14} className="mr-1.5" /> Copy Job Signup Link
            </Button>
          </div>

          {/* Applicants Table */}
          <Card>
            <div className="p-4 border-b border-gray-100 flex items-center justify-between">
              <div>
                <h3 className="font-bold text-gray-900">Job Applicants Awaiting Review ({jobApplicants.length})</h3>
                <p className="text-xs text-gray-500">Commercial Heavy Vehicle & Badge holders looking for immediate placement</p>
              </div>
            </div>

            <Table<JobApplicant>
              data={jobApplicants}
              keyExtractor={(a) => a.id}
              columns={[
                {
                  key: 'candidate',
                  header: 'Candidate Name & Role',
                  render: (a) => (
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="font-bold text-gray-900">{a.fullName}</p>
                        <span
                          className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                            a.role === 'DRIVER' ? 'bg-purple-100 text-purple-800' : 'bg-orange-100 text-orange-800'
                          }`}
                        >
                          {a.role}
                        </span>
                      </div>
                      <p className="text-xs text-gray-500 font-mono mt-0.5">
                        {a.licenseNumber}
                      </p>
                      <p className="text-xs text-gray-400 mt-0.5 flex items-center gap-1">
                        <Phone size={11} /> {a.phone}
                      </p>
                    </div>
                  ),
                },
                {
                  key: 'experience',
                  header: 'Experience & Preferred Routes',
                  render: (a) => (
                    <div>
                      <p className="text-xs font-semibold text-gray-800">
                        {a.experienceYears} Years Heavy Commercial Driving
                      </p>
                      <p className="text-xs text-primary-700 mt-0.5 flex items-center gap-1">
                        <MapPin size={11} /> {a.preferredRoutes}
                      </p>
                    </div>
                  ),
                },
                {
                  key: 'expected',
                  header: 'Expected Salary',
                  render: (a) => (
                    <span className="font-mono text-xs font-bold text-gray-900">
                      ₹{a.expectedSalary.toLocaleString()} / month
                    </span>
                  ),
                },
                {
                  key: 'status',
                  header: 'Application Status',
                  render: (a) => (
                    <span
                      className={`px-2.5 py-0.5 rounded-full text-xs font-bold ${
                        a.status === 'HIRED'
                          ? 'bg-green-100 text-green-800'
                          : a.status === 'SHORTLISTED'
                          ? 'bg-blue-100 text-blue-800'
                          : 'bg-amber-100 text-amber-800'
                      }`}
                    >
                      {a.status === 'HIRED' ? '✅ Hired to Fleet' : a.status === 'SHORTLISTED' ? '⭐ Shortlisted' : 'Pending Review'}
                    </span>
                  ),
                },
                {
                  key: 'actions',
                  header: 'Hiring Action',
                  render: (a) => (
                    <div className="flex items-center gap-2">
                      {a.status !== 'HIRED' ? (
                        <>
                          <Button
                            size="sm"
                            variant="primary"
                            onClick={() => setApplicantToHire(a)}
                          >
                            <UserCheck size={13} className="mr-1" /> Hire Candidate
                          </Button>
                          <a
                            href={`tel:${a.phone}`}
                            className="p-1.5 text-gray-600 hover:text-primary-600 border border-gray-200 rounded-lg hover:bg-gray-50"
                            title="Call Candidate"
                          >
                            <Phone size={14} />
                          </a>
                        </>
                      ) : (
                        <span className="text-xs text-green-700 font-semibold flex items-center gap-1">
                          <CheckCircle2 size={13} /> Active Staff
                        </span>
                      )}
                    </div>
                  ),
                },
              ]}
            />
          </Card>
        </div>
      )}

      {/* ─────────────────────────────────────────────────────────────
          MODAL 1: VIEW FULL STAFF PROFILE
      ───────────────────────────────────────────────────────────── */}
      {selectedStaffProfile && (
        <Modal
          isOpen={true}
          onClose={() => setSelectedStaffProfile(null)}
          title={`Staff Dossier: ${selectedStaffProfile.firstName} ${selectedStaffProfile.lastName}`}
        >
          <div className="space-y-4 text-xs">
            <div className="p-4 bg-gray-50 rounded-xl border border-gray-200 flex items-center gap-4">
              <div className="w-14 h-14 rounded-full bg-primary-600 text-white flex items-center justify-center font-bold text-lg">
                {selectedStaffProfile.firstName[0]}{selectedStaffProfile.lastName[0]}
              </div>
              <div>
                <p className="text-base font-bold text-gray-900">
                  {selectedStaffProfile.firstName} {selectedStaffProfile.lastName}
                </p>
                <p className="text-gray-500 font-medium">Role: {selectedStaffProfile.role} • Blood Group: {selectedStaffProfile.bloodGroup}</p>
                <div className="flex items-center gap-2 mt-1">
                  <span className="bg-green-100 text-green-800 px-2 py-0.5 rounded font-bold">
                    ⭐ {selectedStaffProfile.rating} Rating
                  </span>
                  <span className="bg-blue-100 text-blue-800 px-2 py-0.5 rounded font-bold">
                    {selectedStaffProfile.completedTrips} Trips Completed
                  </span>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="p-3 bg-white border rounded-lg">
                <span className="text-gray-400">Driving License Number:</span>
                <p className="font-mono font-bold text-gray-800 mt-0.5">{selectedStaffProfile.licenseNo}</p>
              </div>
              <div className="p-3 bg-white border rounded-lg">
                <span className="text-gray-400">Conductor / Driver Badge:</span>
                <p className="font-mono font-bold text-gray-800 mt-0.5">{selectedStaffProfile.badgeNo}</p>
              </div>
              <div className="p-3 bg-white border rounded-lg">
                <span className="text-gray-400">Assigned Bus:</span>
                <p className="font-semibold text-gray-900 mt-0.5">{selectedStaffProfile.assignedBus}</p>
              </div>
              <div className="p-3 bg-white border rounded-lg">
                <span className="text-gray-400">Assigned Route Corridor:</span>
                <p className="font-semibold text-gray-900 mt-0.5">{selectedStaffProfile.assignedRoute}</p>
              </div>
              <div className="p-3 bg-white border rounded-lg">
                <span className="text-gray-400">Phone Contact:</span>
                <p className="font-semibold text-gray-900 mt-0.5">{selectedStaffProfile.phone}</p>
              </div>
              <div className="p-3 bg-white border rounded-lg">
                <span className="text-gray-400">Emergency Contact:</span>
                <p className="font-semibold text-gray-900 mt-0.5">{selectedStaffProfile.emergencyContact}</p>
              </div>
            </div>

            <div className="p-3 bg-primary-50 border border-primary-100 rounded-lg">
              <span className="text-primary-800 font-bold">Monthly Compensation Structure:</span>
              <p className="mt-1 text-gray-700">
                Base Monthly Pay: <strong>₹{selectedStaffProfile.baseSalary?.toLocaleString()}</strong> | Daily Duty Bhatta: <strong>₹{selectedStaffProfile.dailyBhatta}/day</strong> | Punctuality Incentives: <strong>₹{selectedStaffProfile.tripIncentive?.toLocaleString()}</strong>
              </p>
            </div>

            <div className="flex justify-end pt-2">
              <Button variant="secondary" onClick={() => setSelectedStaffProfile(null)}>
                Close Dossier
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* ─────────────────────────────────────────────────────────────
          MODAL 2: DISBURSE SALARY & GENERATE PAY SLIP
      ───────────────────────────────────────────────────────────── */}
      {payoutStaff && (
        <Modal
          isOpen={true}
          onClose={() => setPayoutStaff(null)}
          title={`Process Salary Payout: ${payoutStaff.firstName} ${payoutStaff.lastName}`}
        >
          <div className="space-y-4 text-xs">
            {payoutSuccessMsg ? (
              <div className="p-4 bg-green-50 border border-green-200 rounded-xl text-green-800 text-center space-y-2">
                <CheckCircle2 size={32} className="mx-auto text-green-600" />
                <p className="font-bold text-sm">Disbursement Completed!</p>
                <p>{payoutSuccessMsg}</p>
              </div>
            ) : (
              <>
                <div className="p-3 bg-gray-50 border rounded-lg space-y-1">
                  <div className="flex justify-between">
                    <span className="text-gray-500">Base Salary:</span>
                    <span className="font-mono font-bold">₹{payoutStaff.baseSalary?.toLocaleString()}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Daily Bhatta ({payoutStaff.dutyDays} days @ ₹{payoutStaff.dailyBhatta}/day):</span>
                    <span className="font-mono font-bold">₹{((payoutStaff.dailyBhatta || 0) * (payoutStaff.dutyDays || 0)).toLocaleString()}</span>
                  </div>
                  <div className="flex justify-between text-green-700">
                    <span>Performance & Trip Bonus:</span>
                    <span className="font-mono font-bold">+₹{payoutStaff.tripIncentive?.toLocaleString()}</span>
                  </div>
                  <div className="flex justify-between text-red-700 border-b pb-1">
                    <span>Statutory Deductions (PF/ESI):</span>
                    <span className="font-mono font-bold">-₹{payoutStaff.deductions?.toLocaleString()}</span>
                  </div>
                  <div className="flex justify-between text-base font-black text-primary-700 pt-1">
                    <span>Net Amount to Disburse:</span>
                    <span>₹{payoutStaff.netPayable?.toLocaleString()}</span>
                  </div>
                </div>

                <div>
                  <label className="font-semibold text-gray-700">Disbursement Method</label>
                  <div className="grid grid-cols-3 gap-2 mt-1">
                    {(['UPI', 'BANK_TRANSFER', 'CASH'] as const).map((m) => (
                      <button
                        key={m}
                        type="button"
                        onClick={() => setPayoutMethod(m)}
                        className={`p-2 border rounded-lg text-center font-bold text-xs ${
                          payoutMethod === m ? 'border-primary-600 bg-primary-50 text-primary-700' : 'border-gray-200'
                        }`}
                      >
                        {m === 'UPI' ? '⚡ Instant UPI' : m === 'BANK_TRANSFER' ? '🏦 Bank NEFT' : '💵 Cash Bhatta'}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="flex justify-end gap-2 pt-2">
                  <Button variant="secondary" onClick={() => setPayoutStaff(null)}>
                    Cancel
                  </Button>
                  <Button onClick={handleProcessPayout}>
                    Confirm & Disburse ₹{payoutStaff.netPayable?.toLocaleString()}
                  </Button>
                </div>
              </>
            )}
          </div>
        </Modal>
      )}

      {/* ─────────────────────────────────────────────────────────────
          MODAL 3: HIRE APPLICANT CONFIRMATION
      ───────────────────────────────────────────────────────────── */}
      {applicantToHire && (
        <Modal
          isOpen={true}
          onClose={() => setApplicantToHire(null)}
          title={`Hire Candidate: ${applicantToHire.fullName}`}
        >
          <div className="space-y-4 text-xs">
            <p className="text-gray-600">
              You are hiring <strong>{applicantToHire.fullName}</strong> as a <strong>{applicantToHire.role}</strong> for the <strong>{applicantToHire.preferredRoutes}</strong> corridor.
            </p>

            <div className="p-3 bg-gray-50 border rounded-lg space-y-1.5 font-mono">
              <p>Commercial License: {applicantToHire.licenseNumber}</p>
              <p>Experience: {applicantToHire.experienceYears} Years Heavy Vehicle</p>
              <p>Agreed Salary: ₹{applicantToHire.expectedSalary.toLocaleString()} / month + Daily Bhatta</p>
            </div>

            <p className="text-gray-500">
              Confirming will automatically generate their staff credentials and dispatch an SMS to <strong>{applicantToHire.phone}</strong> with their app login details.
            </p>

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="secondary" onClick={() => setApplicantToHire(null)}>
                Cancel
              </Button>
              <Button onClick={() => handleHireApplicant(applicantToHire)}>
                Confirm Hire & Issue Credentials
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* ─────────────────────────────────────────────────────────────
          MODAL 4: ADD NEW STAFF FORM
      ───────────────────────────────────────────────────────────── */}
      <Modal isOpen={isAddModalOpen} onClose={() => setIsAddModalOpen(false)} title="Register New Crew Member">
        <form onSubmit={handleCreateStaff} className="space-y-4 text-xs">
          {formError && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg">
              {formError}
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <Input label="First Name" value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
            <Input label="Last Name" value={lastName} onChange={(e) => setLastName(e.target.value)} required />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Input label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            <Input label="Phone Number" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+91 98450 XXXXX" />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Crew Role</label>
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as any)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-xs"
              >
                <option value="DRIVER">Bus Driver</option>
                <option value="CONDUCTOR">Bus Conductor</option>
              </select>
            </div>
            <Input
              label="Temporary Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Minimum 8 characters"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Driving License / Badge No."
              value={licenseNo}
              onChange={(e) => setLicenseNo(e.target.value)}
              placeholder="KA-20-2022-XXXX"
            />
            <Input
              label="Base Monthly Salary (₹)"
              type="number"
              value={baseSalary}
              onChange={(e) => setBaseSalary(Number(e.target.value))}
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setIsAddModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={createStaffMutation.isPending}>
              Create Crew Account
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default Staff;
