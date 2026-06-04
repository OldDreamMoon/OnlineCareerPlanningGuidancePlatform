export default function FeedbackSection() {
  return (
    <section className="space-y-8" id="feedback">
      <div className="flex items-baseline gap-4">
        <h3 className="font-headline text-2xl font-bold text-on-surface">03. 反馈与状态 / Feedback</h3>
        <span className="h-px flex-1 bg-outline-variant/15"></span>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        <div className="space-y-4">
          <div className="p-4 bg-secondary-container border-l-4 border-secondary rounded-r-lg flex gap-4">
            <span className="material-symbols-outlined text-secondary-dim" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
            <div>
              <p className="font-bold text-on-secondary-container">操作成功</p>
              <p className="text-sm text-on-secondary-container/80">您的个人资料已成功更新至云端服务器。</p>
            </div>
          </div>
          <div className="p-4 bg-error-container border-l-4 border-error rounded-r-lg flex gap-4">
            <span className="material-symbols-outlined text-error-dim" style={{ fontVariationSettings: "'FILL' 1" }}>error</span>
            <div>
              <p className="font-bold text-on-error-container">验证失败</p>
              <p className="text-sm text-on-error-container/80">请检查您的网络连接或重新输入验证码。</p>
            </div>
          </div>
          <div className="p-4 bg-tertiary-container border-l-4 border-tertiary rounded-r-lg flex gap-4">
            <span className="material-symbols-outlined text-tertiary-dim" style={{ fontVariationSettings: "'FILL' 1" }}>warning</span>
            <div>
              <p className="font-bold text-on-tertiary-container">存储预警</p>
              <p className="text-sm text-on-tertiary-container/80">您的存储空间已使用 90%，请及时清理。</p>
            </div>
          </div>
        </div>
        <div className="bg-surface-container-low rounded-lg p-8 flex items-center justify-center min-h-[300px] relative overflow-hidden">
          <div className="absolute inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center p-6">
            <div className="bg-white rounded-lg shadow-2xl w-full max-w-sm overflow-hidden scale-100">
              <div className="p-6 space-y-4">
                <div className="w-12 h-12 bg-primary/10 text-primary rounded-lg flex items-center justify-center">
                  <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>delete_forever</span>
                </div>
                <div>
                  <h4 className="text-lg font-bold text-on-surface">确定要删除此项目吗？</h4>
                  <p className="text-sm text-on-surface-variant">此操作无法撤销，所有相关数据将永久消失。</p>
                </div>
              </div>
              <div className="bg-surface-container-low p-4 flex gap-3 justify-end">
                <button className="px-4 py-2 text-sm font-bold text-on-surface-variant hover:text-on-surface">取消</button>
                <button className="px-4 py-2 text-sm font-bold bg-error text-white rounded-lg shadow-md">确认删除</button>
              </div>
            </div>
          </div>
          <p className="text-xs text-outline font-bold uppercase tracking-widest">模态框预览 (Modal Preview)</p>
        </div>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
        <div className="bg-surface-container-lowest p-8 rounded-lg shadow-sm flex flex-col items-center justify-center gap-6">
          <div className="flex gap-4">
            <div className="w-8 h-8 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
            <div className="flex gap-1">
              <div className="w-2 h-2 bg-primary rounded-full animate-bounce" style={{ animationDelay: "0s" }}></div>
              <div className="w-2 h-2 bg-primary rounded-full animate-bounce" style={{ animationDelay: "0.1s" }}></div>
              <div className="w-2 h-2 bg-primary rounded-full animate-bounce" style={{ animationDelay: "0.2s" }}></div>
            </div>
          </div>
          <p className="text-xs font-bold text-outline">加载中状态</p>
        </div>
        <div className="bg-surface-container-lowest p-8 rounded-lg shadow-sm space-y-4">
          <div className="flex gap-4 items-center">
            <div className="w-12 h-12 bg-surface-container-low rounded-lg animate-pulse"></div>
            <div className="space-y-2 flex-1">
              <div className="h-3 w-2/3 bg-surface-container-low rounded animate-pulse"></div>
              <div className="h-2 w-full bg-surface-container-low rounded animate-pulse"></div>
            </div>
          </div>
          <div className="h-32 w-full bg-surface-container-low rounded-lg animate-pulse"></div>
          <p className="text-xs font-bold text-outline text-center">骨架屏预览</p>
        </div>
        <div className="bg-surface-container-lowest p-8 rounded-lg shadow-sm flex flex-col items-center justify-center gap-4">
          <div className="w-20 h-20 bg-surface-container rounded-full flex items-center justify-center">
            <span className="material-symbols-outlined text-outline text-4xl">inbox_customize</span>
          </div>
          <div className="text-center">
            <p className="font-bold text-on-surface">暂无数据</p>
            <p className="text-xs text-on-surface-variant">当前列表为空，请尝试点击上方按钮添加。</p>
          </div>
        </div>
      </div>
    </section>
  );
}
