import { 
  Verified, 
  Briefcase, 
  Users, 
  GitBranch, 
  ShieldCheck, 
  BarChart3, 
  Activity, 
  Repeat, 
  PlusCircle 
} from 'lucide-react';
import { motion } from 'motion/react';

export function QuickAccess() {
  const actions = [
    { icon: Verified, label: 'Certification' },
    { icon: Briefcase, label: 'Enterprise' },
    { icon: Users, label: 'Mentor Ops' },
    { icon: GitBranch, label: 'Workflows' },
    { icon: ShieldCheck, label: 'Permissions' },
    { icon: BarChart3, label: 'Reports' },
    { icon: Activity, label: 'Metrics' },
    { icon: Repeat, label: 'Pipelines' },
    { icon: PlusCircle, label: 'Setup' },
  ];

  return (
    <section>
      <h2 className="text-2xl font-bold font-headline mb-6">Quick Access</h2>
      <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-9 gap-4">
        {actions.map((action) => (
          <motion.button
            key={action.label}
            whileHover={{ scale: 1.05, backgroundColor: '#ffffff' }}
            whileTap={{ scale: 0.95 }}
            className="flex flex-col items-center gap-3 p-4 glass-card rounded-2xl transition-all shadow-sm hover:shadow-lg"
          >
            <action.icon size={20} className="text-primary" />
            <span className="text-xs font-bold">{action.label}</span>
          </motion.button>
        ))}
      </div>
    </section>
  );
}

export function PendingTasks() {
  const statuses = [
    { label: 'Auth Pending', value: '42', tag: 'HIGH', color: 'primary' },
    { label: 'Task Risk', value: '18', tag: 'ALERT', color: 'error' },
    { label: 'Withdrawal', value: '31', tag: 'MED', color: 'tertiary' },
    { label: 'Verification', value: '37', tag: 'LOW', color: 'secondary' },
  ];

  const colorMap = {
    primary: 'border-primary bg-primary-container/10 text-on-primary-container',
    error: 'border-error bg-error-container/10 text-on-error-container',
    tertiary: 'border-tertiary bg-tertiary-container/10 text-on-tertiary-container',
    secondary: 'border-secondary bg-secondary-container/10 text-on-secondary-container',
  };

  const tagMap = {
    primary: 'bg-primary-container text-on-primary-container',
    error: 'bg-error-container text-on-error-container',
    tertiary: 'bg-tertiary-container text-on-tertiary-container',
    secondary: 'bg-secondary-container text-on-secondary-container',
  };

  return (
    <section>
      <h2 className="text-2xl font-bold font-headline mb-6">Pending Tasks Status</h2>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
        {statuses.map((status) => (
          <div 
            key={status.label}
            className={`bg-surface-container-low p-5 rounded-2xl border-b-2 transition-all hover:shadow-md ${colorMap[status.color as keyof typeof colorMap]}`}
          >
            <div className="flex justify-between items-start">
              <span className="text-on-surface-variant text-sm font-bold">{status.label}</span>
              <span className={`text-[10px] px-2 py-0.5 rounded-full font-black ${tagMap[status.color as keyof typeof tagMap]}`}>
                {status.tag}
              </span>
            </div>
            <div className="text-2xl font-black mt-2 text-on-surface">{status.value}</div>
          </div>
        ))}
      </div>
    </section>
  );
}
