import { Form, Input, InputNumber, Modal, Select } from "antd";
import type { AdminWorkspaceModuleProps } from "../AdminLazyWorkspace";

const { TextArea } = Input;

export default function AdminSkillsOperationsOverlays({ context }: AdminWorkspaceModuleProps<any>) {
  const {
    nodeModalState,
    setNodeModalState,
    handleSubmitNode,
    submittingNode,
    nodeForm,
    resourceModalState,
    setResourceModalState,
    handleSubmitResource,
    submittingResource,
    selectedNode,
    resourceForm,
    resourceTypeOptions,
    nodeDeleteConfirmOpen,
    setNodeDeleteConfirmOpen,
    deletingNode,
    handleDeleteNode,
    relationDeleteTarget,
    setRelationDeleteTarget,
    deletingRelation,
    handleDeleteRelation,
    getRelationTypeLabel,
  } = context;

  return (
    <>
      <Modal
        title={
          nodeModalState.mode === "edit"
            ? "编辑节点"
            : nodeModalState.mode === "create-child"
              ? "新建子节点"
              : "新建根节点"
        }
        open={nodeModalState.open}
        onCancel={() => setNodeModalState({ open: false, mode: nodeModalState.mode, target: null, parentContext: null })}
        onOk={() => void handleSubmitNode()}
        confirmLoading={submittingNode}
        okText={nodeModalState.mode === "edit" ? "保存修改" : "创建节点"}
        cancelText="取消"
        destroyOnHidden
      >
        {nodeModalState.mode === "create-child" && nodeModalState.parentContext ? (
          <div className="mb-4 rounded-2xl bg-slate-50 px-4 py-3">
            <div className="text-[11px] font-bold text-slate-400">父节点</div>
            <div className="mt-2 text-sm font-semibold text-slate-900">
              {nodeModalState.parentContext.label} · {nodeModalState.parentContext.nodeCode}
            </div>
          </div>
        ) : null}

        <Form layout="vertical" form={nodeForm}>
          <Form.Item label="节点编码" name="nodeCode" rules={[{ required: true, message: "请输入节点编码" }]}>
            <Input disabled={nodeModalState.mode === "edit"} />
          </Form.Item>
          <Form.Item label="节点名称" name="label" rules={[{ required: true, message: "请输入节点名称" }]}>
            <Input />
          </Form.Item>
          <Form.Item label="节点描述" name="description">
            <TextArea rows={4} />
          </Form.Item>
          <Form.Item label="排序值" name="sortOrder">
            <InputNumber className="!w-full" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={resourceModalState.mode === "create" ? "添加资源" : "编辑资源"}
        open={resourceModalState.open}
        onCancel={() => setResourceModalState({ open: false, mode: resourceModalState.mode, target: null })}
        onOk={() => void handleSubmitResource()}
        confirmLoading={submittingResource}
        okText={resourceModalState.mode === "create" ? "添加资源" : "保存修改"}
        cancelText="取消"
        destroyOnHidden
      >
        {selectedNode ? (
          <div className="mb-4 rounded-2xl bg-slate-50 px-4 py-3">
            <div className="text-[11px] font-bold text-slate-400">绑定节点</div>
            <div className="mt-2 text-sm font-semibold text-slate-900">
              {selectedNode.label} · {selectedNode.nodeCode}
            </div>
          </div>
        ) : null}

        <Form layout="vertical" form={resourceForm}>
          <Form.Item label="资源编码" name="resourceCode" rules={[{ required: true, message: "请输入资源编码" }]}>
            <Input disabled={resourceModalState.mode === "edit"} />
          </Form.Item>
          <div className="grid gap-4 md:grid-cols-2">
            <Form.Item label="资源类型" name="resourceType" rules={[{ required: true, message: "请选择资源类型" }]}>
              <Select options={resourceTypeOptions} />
            </Form.Item>
            <Form.Item label="排序值" name="sortOrder">
              <InputNumber className="!w-full" />
            </Form.Item>
          </div>
          <Form.Item label="资源标题" name="title" rules={[{ required: true, message: "请输入资源标题" }]}>
            <Input />
          </Form.Item>
          <div className="grid gap-4 md:grid-cols-2">
            <Form.Item label="来源说明" name="sourceLabel" rules={[{ required: true, message: "请输入来源说明" }]}>
              <Input />
            </Form.Item>
            <Form.Item label="时长 / 标签" name="durationLabel" rules={[{ required: true, message: "请输入时长或标签" }]}>
              <Input />
            </Form.Item>
          </div>
          <Form.Item label="链接地址" name="linkUrl" rules={[{ required: true, message: "请输入链接地址" }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="删除节点"
        open={nodeDeleteConfirmOpen}
        onCancel={() => setNodeDeleteConfirmOpen(false)}
        onOk={() => void handleDeleteNode()}
        confirmLoading={deletingNode}
        okText="删除节点"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        destroyOnHidden
      >
        <div className="rounded-2xl bg-rose-50 px-4 py-4 text-sm leading-6 text-rose-700">
          {selectedNode ? `将删除节点 ${selectedNode.label}，该操作不可撤销。` : "当前没有可删除的节点。"}
        </div>
      </Modal>

      <Modal
        title="删除关联"
        open={Boolean(relationDeleteTarget)}
        onCancel={() => setRelationDeleteTarget(null)}
        onOk={() => {
          if (relationDeleteTarget) {
            void handleDeleteRelation(relationDeleteTarget);
          }
        }}
        confirmLoading={deletingRelation}
        okText="删除"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        destroyOnHidden
      >
        <div className="rounded-2xl bg-rose-50 px-4 py-4 text-sm leading-6 text-rose-700">
          {relationDeleteTarget
            ? `将移除 ${relationDeleteTarget.relation.label || getRelationTypeLabel(relationDeleteTarget.relation.relationType)} 这条节点关联。`
            : "当前没有可删除的节点关联。"}
        </div>
      </Modal>
    </>
  );
}
