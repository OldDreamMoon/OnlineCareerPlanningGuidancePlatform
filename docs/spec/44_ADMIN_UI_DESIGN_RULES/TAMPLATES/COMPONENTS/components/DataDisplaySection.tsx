export default function DataDisplaySection() {
  return (
    <section className="space-y-8" id="display">
      <div className="flex items-baseline gap-4">
        <h3 className="font-headline text-2xl font-bold text-on-surface">04. 数据展示 / Data Display</h3>
        <span className="h-px flex-1 bg-outline-variant/15"></span>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="bg-white p-6 rounded-lg shadow-sm space-y-4 border-t-4 border-primary">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-bold text-outline uppercase">总访问量</p>
              <h4 className="text-2xl font-extrabold mt-1">124,592</h4>
            </div>
            <div className="p-2 bg-primary/10 text-primary rounded-lg">
              <span className="material-symbols-outlined">trending_up</span>
            </div>
          </div>
          <div className="h-12 w-full flex items-end gap-1">
            <div className="flex-1 bg-primary/20 h-1/2 rounded-t-sm"></div>
            <div className="flex-1 bg-primary/20 h-2/3 rounded-t-sm"></div>
            <div className="flex-1 bg-primary h-full rounded-t-sm"></div>
            <div className="flex-1 bg-primary/20 h-3/4 rounded-t-sm"></div>
            <div className="flex-1 bg-primary/20 h-1/2 rounded-t-sm"></div>
          </div>
        </div>
        <div className="bg-white p-6 rounded-lg shadow-sm space-y-4 border-t-4 border-secondary-container">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-bold text-outline uppercase">活跃用户</p>
              <h4 className="text-2xl font-extrabold mt-1">8,122</h4>
            </div>
            <div className="p-2 bg-secondary-container/10 text-secondary rounded-lg">
              <span className="material-symbols-outlined">group</span>
            </div>
          </div>
          <div className="h-12 w-full flex items-end gap-1">
            <div className="flex-1 bg-secondary-fixed h-1/3 rounded-t-sm"></div>
            <div className="flex-1 bg-secondary-fixed h-1/2 rounded-t-sm"></div>
            <div className="flex-1 bg-secondary-fixed h-2/3 rounded-t-sm"></div>
            <div className="flex-1 bg-secondary-fixed h-1/2 rounded-t-sm"></div>
            <div className="flex-1 bg-secondary-fixed h-3/4 rounded-t-sm"></div>
          </div>
        </div>
        <div className="bg-white p-6 rounded-lg shadow-sm space-y-4 border-t-4 border-tertiary-container">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-bold text-outline uppercase">平均转化率</p>
              <h4 className="text-2xl font-extrabold mt-1">14.2%</h4>
            </div>
            <div className="p-2 bg-tertiary-container/10 text-tertiary rounded-lg">
              <span className="material-symbols-outlined">bolt</span>
            </div>
          </div>
          <div className="h-12 w-full flex items-end gap-1">
            <div className="flex-1 bg-tertiary-fixed h-3/4 rounded-t-sm"></div>
            <div className="flex-1 bg-tertiary-fixed h-1/2 rounded-t-sm"></div>
            <div className="flex-1 bg-tertiary-fixed h-2/3 rounded-t-sm"></div>
            <div className="flex-1 bg-tertiary-fixed h-1/3 rounded-t-sm"></div>
            <div className="flex-1 bg-tertiary-fixed h-1/2 rounded-t-sm"></div>
          </div>
        </div>
        <div className="bg-white p-6 rounded-lg shadow-sm space-y-4 border-t-4 border-error-container">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-bold text-outline uppercase">跳出率</p>
              <h4 className="text-2xl font-extrabold mt-1">2.4%</h4>
            </div>
            <div className="p-2 bg-error-container/10 text-error rounded-lg">
              <span className="material-symbols-outlined">call_missed_outgoing</span>
            </div>
          </div>
          <div className="h-12 w-full flex items-end gap-1">
            <div className="flex-1 bg-error-container h-1/2 rounded-t-sm"></div>
            <div className="flex-1 bg-error-container h-1/3 rounded-t-sm"></div>
            <div className="flex-1 bg-error-container h-1/4 rounded-t-sm"></div>
            <div className="flex-1 bg-error-container h-1/2 rounded-t-sm"></div>
            <div className="flex-1 bg-error-container h-2/3 rounded-t-sm"></div>
          </div>
        </div>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        <div className="bg-surface-container-lowest p-8 rounded-lg shadow-sm space-y-8">
          <div className="space-y-4">
            <p className="text-xs font-bold text-outline uppercase">标签与徽标 (Tags &amp; Badges)</p>
            <div className="flex flex-wrap gap-3">
              <span className="px-3 py-1 bg-primary-container text-primary-dim text-xs font-bold rounded-lg">Indigo Tag</span>
              <span className="px-3 py-1 bg-secondary-container text-secondary-dim text-xs font-bold rounded-lg">Success</span>
              <span className="px-3 py-1 bg-tertiary-container text-tertiary-dim text-xs font-bold rounded-lg">Warning</span>
              <span className="px-3 py-1 bg-error-container text-error-dim text-xs font-bold rounded-lg">Critical</span>
            </div>
            <div className="flex gap-4">
              <div className="relative">
                <button className="w-10 h-10 bg-surface-container rounded-lg flex items-center justify-center"><span className="material-symbols-outlined">notifications</span></button>
                <span className="absolute -top-1 -right-1 w-4 h-4 bg-error text-white text-[8px] flex items-center justify-center rounded-full font-bold">9+</span>
              </div>
              <div className="relative">
                <button className="w-10 h-10 bg-surface-container rounded-lg flex items-center justify-center"><span className="material-symbols-outlined">mail</span></button>
                <span className="absolute top-1 right-1 w-2.5 h-2.5 bg-secondary-fixed border-2 border-white rounded-full"></span>
              </div>
            </div>
          </div>
          <div className="space-y-4">
            <p className="text-xs font-bold text-outline uppercase">头像系统 (Avatars)</p>
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-lg bg-primary-container overflow-hidden">
                <img alt="User Avatar" className="w-full h-full object-cover" src="https://lh3.googleusercontent.com/aida-public/AB6AXuBZAK7KhYRD4uz0RKR_SDDWXSRI9GyqXV25wUbkhHc7vF0ta4hwBfo2hzAoJ66ej4KzGtx3PW7kG3FDuHEnni4XERagGrQNp044ACc1AcqVLXZxql1bhmPptSnrydpp4dONfsQxt_nsnzhk9bT5qDzPdD44mHp6QUdikYa53_c-IfrmCNPDONrgdX7iny16Q5gOq5am_m8IIaEecvZ_YvWq3aUdQhdzdB7cfumVpQvmVzii5LviyWSGC2855c1A-hdk2HJI-DfmcHE" referrerPolicy="no-referrer" />
              </div>
              <div className="w-10 h-10 rounded-full border-2 border-primary overflow-hidden">
                <img alt="User Avatar" className="w-full h-full object-cover" src="https://lh3.googleusercontent.com/aida-public/AB6AXuDhyjljxJx_gFkBJQ_IUqhKSyk0vBUbnsHVPVtOidr-7OMTNWBtdoiklNBjvOgzBttEjtnfVh7dPLX43txEJ4TTRQNsBR1kr9RFoZu6SHbI32xFBWZeuYoLtvmwDdTJnM2TWzq0ZtFnVQ0AlOAn4M3bfc63HvwuV5PznE5_l2A4KkISWuD_vi9UHF2sFc_dy83s_W_QwcJ-Ja72YAffo72GJyboxkBs1hTiIVBvzRsyalDYGwii1nYL_ubtnNK6DO12pL1fve8BlgE" referrerPolicy="no-referrer" />
              </div>
              <div className="flex -space-x-3">
                <div className="w-10 h-10 rounded-lg bg-surface-container-high border-2 border-white"></div>
                <div className="w-10 h-10 rounded-lg bg-primary-fixed border-2 border-white"></div>
                <div className="w-10 h-10 rounded-lg bg-secondary-fixed border-2 border-white"></div>
                <div className="w-10 h-10 rounded-lg bg-on-surface text-white flex items-center justify-center text-[10px] font-bold border-2 border-white">+12</div>
              </div>
            </div>
          </div>
        </div>
        <div className="bg-surface-container-lowest p-8 rounded-lg shadow-sm space-y-8">
          <div className="space-y-4">
            <p className="text-xs font-bold text-outline uppercase">进度指示器 (Progress &amp; Steps)</p>
            <div className="space-y-6">
              <div className="space-y-2">
                <div className="flex justify-between text-xs font-bold">
                  <span>系统同步中...</span>
                  <span>72%</span>
                </div>
                <div className="w-full h-3 bg-surface-container-low rounded-lg overflow-hidden relative">
                  <div className="h-full bg-gradient-to-r from-primary to-secondary-fixed w-[72%] rounded-lg shadow-[0_0_10px_rgba(70,71,211,0.3)]"></div>
                </div>
              </div>
              <div className="flex justify-between relative px-2">
                <div className="absolute top-1/2 left-0 w-full h-0.5 bg-surface-container-low -translate-y-1/2 z-0"></div>
                <div className="relative z-10 flex flex-col items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-primary text-white flex items-center justify-center text-xs font-bold">1</div>
                  <span className="text-[10px] font-bold">提交申请</span>
                </div>
                <div className="relative z-10 flex flex-col items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-primary text-white flex items-center justify-center text-xs font-bold">2</div>
                  <span className="text-[10px] font-bold">后台审核</span>
                </div>
                <div className="relative z-10 flex flex-col items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-surface-container-high text-on-surface-variant flex items-center justify-center text-xs font-bold">3</div>
                  <span className="text-[10px] font-bold text-outline">完成签署</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
