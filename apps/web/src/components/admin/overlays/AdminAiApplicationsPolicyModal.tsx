import { Alert, Empty, Form, Input, InputNumber, Modal } from "antd";
import type { AdminWorkspaceModuleProps } from "../AdminLazyWorkspace";

export default function AdminAiApplicationsPolicyModal({ context }: AdminWorkspaceModuleProps<any>) {
  const {
    policyEditorOpen,
    editingPolicy,
    editingPolicyTargetLabel,
    policySaving,
    handleSavePolicy,
    setPolicyEditorOpen,
    setEditingPolicyTargetLabel,
    editingPolicySharedScenes,
    policyForm,
  } = context;

  return (
    <Modal
      open={policyEditorOpen}
      width={640}
      destroyOnClose
      title={editingPolicy ? `${editingPolicyTargetLabel} · ${editingPolicy.tier === "PREMIUM" ? "VIP" : "普通"}权益` : "编辑权益策略"}
      okText="保存"
      cancelText="取消"
      confirmLoading={policySaving}
      onOk={() => void handleSavePolicy()}
      onCancel={() => {
        setPolicyEditorOpen(false);
        setEditingPolicyTargetLabel("");
      }}
    >
      {editingPolicy ? (
        <div className="space-y-4">
          <Alert
            type={editingPolicySharedScenes.length > 1 ? "warning" : "info"}
            showIcon
            className="rounded-2xl"
            message={editingPolicySharedScenes.length > 1 ? "当前为共享权益组" : "当前为独立权益组"}
            description={editingPolicySharedScenes.length > 1
              ? `本次修改会同时影响：${editingPolicySharedScenes.map((scene: any) => scene.displayName).join("、")}`
              : "保存后仅影响当前这一项场景。"}
          />
          <Form form={policyForm} layout="vertical">
            <div className="grid gap-4 md:grid-cols-2">
              <Form.Item label="每日免费额度" name="dailyFreeLimit" rules={[{ required: true, message: "请输入每日免费额度" }]}>
                <InputNumber className="w-full" min={-1} max={100000} />
              </Form.Item>
              <Form.Item label="单次积分" name="pointsPerCall" rules={[{ required: true, message: "请输入单次积分" }]}>
                <InputNumber className="w-full" min={0} max={100000} />
              </Form.Item>
              <Form.Item label="每日上限" name="dailyMaxLimit" rules={[{ required: true, message: "请输入每日上限" }]}>
                <InputNumber className="w-full" min={-1} max={100000} />
              </Form.Item>
              <Form.Item label="单次内容上限" name="maxInputTokens" rules={[{ required: true, message: "请输入单次内容上限" }]}>
                <InputNumber className="w-full" min={1} max={200000} />
              </Form.Item>
            </div>
            <Form.Item label="优先模型" name="modelPreference">
              <Input placeholder="填写模型标识，留空则沿用默认设置" />
            </Form.Item>
          </Form>
        </div>
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有可编辑的权益策略" />
      )}
    </Modal>
  );
}
