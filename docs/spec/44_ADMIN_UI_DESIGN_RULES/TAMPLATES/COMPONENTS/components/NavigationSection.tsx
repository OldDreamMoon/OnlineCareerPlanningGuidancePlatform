export default function NavigationSection() {
  return (
    <section className="space-y-8" id="navigation">
      <div className="flex items-baseline gap-4">
        <h3 className="font-headline text-2xl font-bold text-on-surface">05. 导航与布局 / Navigation</h3>
        <span className="h-px flex-1 bg-outline-variant/15"></span>
      </div>
      <div className="space-y-8">
        <div className="bg-surface-container-lowest p-8 rounded-lg shadow-sm flex flex-col md:flex-row gap-12 items-start md:items-center justify-between">
          <nav className="flex items-center gap-2 text-sm">
            <a className="text-on-surface-variant hover:text-primary" href="#">首页</a>
            <span className="material-symbols-outlined text-outline text-sm">chevron_right</span>
            <a className="text-on-surface-variant hover:text-primary" href="#">系统设置</a>
            <span className="material-symbols-outlined text-outline text-sm">chevron_right</span>
            <span className="text-primary font-bold">组件概览</span>
          </nav>
          <div className="flex gap-2">
            <button className="w-10 h-10 flex items-center justify-center rounded-lg bg-surface-container-low text-on-surface-variant hover:bg-primary hover:text-white transition-all"><span className="material-symbols-outlined">chevron_left</span></button>
            <button className="w-10 h-10 flex items-center justify-center rounded-lg bg-primary text-white font-bold shadow-md">1</button>
            <button className="w-10 h-10 flex items-center justify-center rounded-lg bg-white text-on-surface-variant hover:bg-surface-container-low font-bold">2</button>
            <button className="w-10 h-10 flex items-center justify-center rounded-lg bg-white text-on-surface-variant hover:bg-surface-container-low font-bold">3</button>
            <span className="w-10 h-10 flex items-center justify-center text-outline">...</span>
            <button className="w-10 h-10 flex items-center justify-center rounded-lg bg-surface-container-low text-on-surface-variant hover:bg-primary hover:text-white transition-all"><span className="material-symbols-outlined">chevron_right</span></button>
          </div>
        </div>
        <div className="bg-surface-container-lowest p-2 rounded-lg shadow-sm border border-outline-variant/5">
          <div className="flex bg-surface-container-low p-1 rounded-lg">
            <button className="flex-1 py-3 px-6 text-sm font-bold bg-white text-primary rounded-lg shadow-sm">核心组件 (Core)</button>
            <button className="flex-1 py-3 px-6 text-sm font-bold text-on-surface-variant hover:text-on-surface transition-colors">数据分析 (Data)</button>
            <button className="flex-1 py-3 px-6 text-sm font-bold text-on-surface-variant hover:text-on-surface transition-colors">权限管理 (Auth)</button>
            <button className="flex-1 py-3 px-6 text-sm font-bold text-on-surface-variant hover:text-on-surface transition-colors">系统日志 (Logs)</button>
          </div>
          <div className="p-8 text-center">
            <div className="max-w-md mx-auto space-y-4">
              <h5 className="text-xl font-bold">核心组件视图</h5>
              <p className="text-sm text-on-surface-variant">在此标签页下，您可以预览系统中最基础的原子级组件及其交互规则。</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
