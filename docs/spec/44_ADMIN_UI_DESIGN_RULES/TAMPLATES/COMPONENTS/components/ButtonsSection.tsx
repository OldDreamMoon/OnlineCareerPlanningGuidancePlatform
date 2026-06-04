export default function ButtonsSection() {
  return (
    <section className="space-y-8" id="buttons">
      <div className="flex items-baseline gap-4">
        <h3 className="font-headline text-2xl font-bold text-on-surface">01. 按钮组件 / Buttons</h3>
        <span className="h-px flex-1 bg-outline-variant/15"></span>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8">
        <div className="space-y-4 bg-surface-container-lowest p-8 rounded-lg shadow-sm">
          <p className="text-xs font-bold text-outline uppercase tracking-widest mb-6">基础样式</p>
          <div className="flex flex-col gap-4">
            <button className="w-full bg-gradient-to-br from-primary to-primary-dim text-white py-3 px-6 rounded-lg font-semibold shadow-md hover:shadow-lg transition-all active:scale-95">主要按钮 (Primary)</button>
            <button className="w-full bg-surface-container text-on-surface py-3 px-6 rounded-lg font-semibold hover:bg-surface-container-high transition-colors">次要按钮 (Default)</button>
            <button className="w-full bg-transparent text-primary py-3 px-6 rounded-lg font-semibold hover:bg-primary-container/10 transition-colors">幽灵按钮 (Ghost)</button>
            <button className="w-full bg-error text-white py-3 px-6 rounded-lg font-semibold shadow-md hover:bg-error-dim transition-colors">危险按钮 (Danger)</button>
          </div>
        </div>
        <div className="space-y-4 bg-surface-container-lowest p-8 rounded-lg shadow-sm">
          <p className="text-xs font-bold text-outline uppercase tracking-widest mb-6">交互状态</p>
          <div className="flex flex-col gap-4">
            <button className="w-full bg-primary text-white py-3 px-6 rounded-lg font-semibold opacity-50 cursor-not-allowed">禁用状态 (Disabled)</button>
            <button className="w-full bg-primary text-white py-3 px-6 rounded-lg font-semibold flex items-center justify-center gap-2">
              <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
              加载中 (Loading)
            </button>
            <div className="flex rounded-lg overflow-hidden bg-surface-container shadow-inner p-1">
              <button className="flex-1 py-2 text-sm font-bold bg-white text-primary rounded-lg">左侧</button>
              <button className="flex-1 py-2 text-sm font-bold text-on-surface-variant hover:text-on-surface">中间</button>
              <button className="flex-1 py-2 text-sm font-bold text-on-surface-variant hover:text-on-surface">右侧</button>
            </div>
          </div>
        </div>
        <div className="col-span-1 lg:col-span-2 bg-primary/5 rounded-lg p-8 flex flex-col items-center justify-center relative overflow-hidden">
          <div className="absolute -right-10 -bottom-10 w-40 h-40 bg-primary/10 rounded-full blur-3xl"></div>
          <div className="grid grid-cols-4 gap-4 relative z-10">
            <button className="w-12 h-12 bg-white rounded-lg flex items-center justify-center text-primary shadow-sm hover:scale-110 transition-transform"><span className="material-symbols-outlined">favorite</span></button>
            <button className="w-12 h-12 bg-primary text-white rounded-lg flex items-center justify-center shadow-md hover:scale-110 transition-transform"><span className="material-symbols-outlined">add</span></button>
            <button className="w-12 h-12 bg-secondary-container text-on-secondary-container rounded-lg flex items-center justify-center shadow-sm hover:scale-110 transition-transform"><span className="material-symbols-outlined">share</span></button>
            <button className="w-12 h-12 bg-error-container text-on-error-container rounded-lg flex items-center justify-center shadow-sm hover:scale-110 transition-transform"><span className="material-symbols-outlined">delete</span></button>
          </div>
          <p className="mt-6 text-sm text-primary font-semibold">图标按钮与颜色变体</p>
        </div>
      </div>
    </section>
  );
}
