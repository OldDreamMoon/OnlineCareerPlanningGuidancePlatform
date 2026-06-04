import { TrendingUp, CheckCircle2, AlertTriangle } from 'lucide-react';

export function DomainSnapshots() {
  return (
    <section>
      <h2 className="text-2xl font-bold font-headline mb-6">Domain Snapshots</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* User & Identity Card */}
        <div className="glass-card rounded-3xl overflow-hidden flex flex-col shadow-sm">
          <div className="p-6 bg-gradient-to-r from-primary to-primary-container text-white">
            <h3 className="text-xl font-bold font-headline">User & Identity Domain</h3>
            <p className="text-xs opacity-80">Security and authentication metrics</p>
          </div>
          <div className="p-8 grid grid-cols-2 gap-6">
            {[
              { label: 'Active Users', value: '1.2M', trend: '4.2%' },
              { label: 'New Enterprise', value: '842', trend: '12.5%' },
              { label: 'MFA Adoption', value: '78%' },
              { label: 'SSO Uplift', value: '14%' },
            ].map((stat) => (
              <div key={stat.label} className="space-y-1">
                <p className="text-xs font-bold text-on-surface-variant uppercase tracking-tighter">{stat.label}</p>
                <p className="text-2xl font-black">{stat.value}</p>
                {stat.trend && (
                  <div className="flex items-center gap-1 text-[10px] text-secondary font-bold">
                    <TrendingUp size={12} /> {stat.trend}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* AI & Skill Domain */}
        <div className="glass-card rounded-3xl overflow-hidden flex flex-col shadow-sm">
          <div className="p-6 bg-slate-900 text-white">
            <h3 className="text-xl font-bold font-headline">AI & Skill Runtime</h3>
            <p className="text-xs opacity-80">Execution performance and skill health</p>
          </div>
          <div className="p-8 space-y-6">
            {[
              { label: 'Skill Success Rate', value: '99.8%', color: 'bg-secondary' },
              { label: 'Average Latency', value: '124ms', color: 'bg-tertiary' },
              { label: 'Prompt Efficiency', value: '86.2%', color: 'bg-primary' },
            ].map((stat) => (
              <div key={stat.label} className="flex justify-between items-center">
                <div className="flex items-center gap-3">
                  <div className={`w-2 h-2 rounded-full ${stat.color}`}></div>
                  <span className="text-sm font-semibold">{stat.label}</span>
                </div>
                <span className="text-lg font-black">{stat.value}</span>
              </div>
            ))}
            <div className="pt-2">
              <div className="h-2 w-full bg-surface-container-low rounded-full overflow-hidden">
                <div 
                  className="h-full bg-gradient-to-r from-primary to-secondary transition-all duration-1000" 
                  style={{ width: '86%' }}
                ></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

export function RuntimeSummary() {
  return (
    <section className="glass-card p-8 rounded-3xl shadow-sm">
      <div className="flex justify-between items-center mb-8">
        <h2 className="text-2xl font-bold font-headline">AI Runtime Summary</h2>
        <span className="text-xs font-bold py-1 px-3 bg-secondary-container text-on-secondary-container rounded-full">
          LIVE MONITORING
        </span>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="p-6 bg-surface-container-low rounded-2xl">
          <p className="text-xs font-bold text-on-surface-variant mb-2 uppercase">Active Scenes</p>
          <div className="text-4xl font-black text-primary">1,402</div>
          <p className="text-[10px] font-bold mt-4 flex items-center gap-1">
            <CheckCircle2 size={12} className="text-secondary" />
            All routes optimal
          </p>
        </div>
        <div className="p-6 bg-surface-container-low rounded-2xl">
          <p className="text-xs font-bold text-on-surface-variant mb-2 uppercase">High Cost Scenes</p>
          <div className="text-4xl font-black text-tertiary">12</div>
          <p className="text-[10px] font-bold mt-4 flex items-center gap-1">
            <TrendingUp size={12} className="text-tertiary" />
            Optimizing prompt #42
          </p>
        </div>
        <div className="p-6 bg-surface-container-low rounded-2xl">
          <p className="text-xs font-bold text-on-surface-variant mb-2 uppercase">Success Anomalies</p>
          <div className="text-4xl font-black text-error">3</div>
          <p className="text-[10px] font-bold mt-4 flex items-center gap-1">
            <AlertTriangle size={12} className="text-error" />
            Investigating 404s
          </p>
        </div>
      </div>
    </section>
  );
}
