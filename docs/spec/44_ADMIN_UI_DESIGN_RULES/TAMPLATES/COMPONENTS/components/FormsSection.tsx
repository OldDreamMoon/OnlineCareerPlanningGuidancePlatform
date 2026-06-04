export default function FormsSection() {
  return (
    <section className="space-y-8" id="forms">
      <div className="flex items-baseline gap-4">
        <h3 className="font-headline text-2xl font-bold text-on-surface">02. 表单与输入 / Form &amp; Inputs</h3>
        <span className="h-px flex-1 bg-outline-variant/15"></span>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
        <div className="bg-surface-container-lowest p-8 rounded-lg shadow-sm space-y-6">
          <div className="space-y-2">
            <label className="text-sm font-bold text-on-surface-variant">文本输入 (前缀与后缀)</label>
            <div className="relative flex items-center">
              <span className="absolute left-4 material-symbols-outlined text-outline">mail</span>
              <input className="w-full pl-12 pr-12 py-3 bg-surface-container-low border-none rounded-lg focus:ring-2 focus:ring-primary focus:bg-white transition-all text-sm outline-none" placeholder="example@domain.com" type="text" />
              <span className="absolute right-4 text-xs font-bold text-outline">.com</span>
            </div>
          </div>
          <div className="space-y-2">
            <label className="text-sm font-bold text-on-surface-variant">数字调节</label>
            <div className="flex bg-surface-container-low rounded-lg p-1">
              <button className="w-10 h-10 flex items-center justify-center hover:bg-white rounded-lg transition-colors"><span className="material-symbols-outlined">remove</span></button>
              <input className="flex-1 bg-transparent border-none text-center focus:ring-0 font-bold outline-none" type="number" defaultValue="12" />
              <button className="w-10 h-10 flex items-center justify-center hover:bg-white rounded-lg transition-colors"><span className="material-symbols-outlined">add</span></button>
            </div>
          </div>
        </div>
        <div className="bg-surface-container-lowest p-8 rounded-lg shadow-sm space-y-6">
          <div className="space-y-4">
            <label className="text-sm font-bold text-on-surface-variant">多选与单选</label>
            <div className="flex flex-wrap gap-4">
              <label className="flex items-center gap-2 cursor-pointer group">
                <div className="w-5 h-5 rounded border-2 border-outline-variant group-hover:border-primary flex items-center justify-center transition-colors relative">
                  <input defaultChecked className="hidden peer" type="checkbox" />
                  <div className="w-2.5 h-2.5 bg-primary rounded-[2px] opacity-0 peer-checked:opacity-100 transition-opacity"></div>
                </div>
                <span className="text-sm text-on-surface font-medium">选项 A</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer group">
                <div className="w-5 h-5 rounded-full border-2 border-outline-variant group-hover:border-primary flex items-center justify-center transition-colors relative">
                  <input defaultChecked className="hidden peer" name="r1" type="radio" />
                  <div className="w-2.5 h-2.5 bg-primary rounded-full opacity-0 peer-checked:opacity-100 transition-opacity"></div>
                </div>
                <span className="text-sm text-on-surface font-medium">单选 1</span>
              </label>
            </div>
          </div>
          <div className="space-y-4">
            <label className="text-sm font-bold text-on-surface-variant">开关与滑块</label>
            <div className="flex items-center justify-between">
              <span className="text-sm">启用实时预览</span>
              <label className="relative inline-flex items-center cursor-pointer">
                <input defaultChecked className="sr-only peer" type="checkbox" value="" />
                <div className="w-11 h-6 bg-surface-container peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary"></div>
              </label>
            </div>
            <input className="w-full h-2 bg-surface-container-low rounded-lg appearance-none cursor-pointer accent-primary" type="range" />
          </div>
        </div>
        <div className="bg-surface-container-lowest p-8 rounded-lg shadow-sm space-y-6">
          <div className="space-y-2">
            <label className="text-sm font-bold text-on-surface-variant">日期选择器 (示例)</label>
            <div className="bg-surface-container-low p-3 rounded-lg flex items-center justify-between">
              <span className="text-sm font-medium">2024年10月24日</span>
              <span className="material-symbols-outlined text-primary">calendar_month</span>
            </div>
          </div>
          <div className="space-y-2">
            <label className="text-sm font-bold text-on-surface-variant">多选标签下拉框</label>
            <div className="bg-surface-container-low p-2 rounded-lg flex flex-wrap gap-2">
              <span className="bg-primary text-white px-2 py-1 rounded text-[10px] font-bold flex items-center gap-1">
                设计师 <span className="material-symbols-outlined text-[12px]">close</span>
              </span>
              <span className="bg-primary text-white px-2 py-1 rounded text-[10px] font-bold flex items-center gap-1">
                前端 <span className="material-symbols-outlined text-[12px]">close</span>
              </span>
              <input className="flex-1 min-w-[50px] bg-transparent border-none p-0 focus:ring-0 text-sm outline-none" type="text" />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
