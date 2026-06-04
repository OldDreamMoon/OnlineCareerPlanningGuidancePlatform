package com.bishe.server.notification.service;

import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知事件注册表：定义默认标题、分类、优先级与跳转语义。
 */
@Component
public class NotificationDescriptorRegistry {

    private final Map<String, NotificationDescriptor> descriptors;

    public NotificationDescriptorRegistry() {
        Map<String, NotificationDescriptor> registry = new LinkedHashMap<>();
        // AI 事件统一回 AI 历史/任务结果页，异步任务和普通复盘共用分类。
        registry.put("AI_RESUME_TASK_SUCCEEDED", descriptor(NotificationCategory.AI_TASK, NotificationPriority.NORMAL, "AI 简历任务已完成", "AI_ASYNC_TASK", "VIEW_AI_RESUME_TASK_RESULT", true));
        registry.put("AI_RESUME_TASK_FAILED", descriptor(NotificationCategory.AI_TASK, NotificationPriority.HIGH, "AI 简历任务执行失败", "AI_ASYNC_TASK", "VIEW_AI_RESUME_TASK_STATUS", true));
        registry.put("AI_INTERVIEW_SUMMARY_READY", descriptor(NotificationCategory.AI_TASK, NotificationPriority.NORMAL, "AI 模拟面试复盘已生成", "AI_INTERVIEW", "VIEW_AI_REVIEW_CENTER", true));

        // 咨询事件全部以订单号作为 refId，前端根据角色跳学生详情或导师履约区。
        registry.put("CONSULT_PAID", descriptor(NotificationCategory.CONSULT, NotificationPriority.HIGH, "咨询订单已支付", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_REPLIED", descriptor(NotificationCategory.CONSULT, NotificationPriority.HIGH, "导师已回复你的咨询", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_CANCELED", descriptor(NotificationCategory.CONSULT, NotificationPriority.NORMAL, "咨询订单已取消", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_TIMEOUT_CANCELED", descriptor(NotificationCategory.CONSULT, NotificationPriority.NORMAL, "咨询订单已超时关闭", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_CLOSED", descriptor(NotificationCategory.CONSULT, NotificationPriority.NORMAL, "咨询订单已关闭", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_REVIEWED", descriptor(NotificationCategory.CONSULT, NotificationPriority.NORMAL, "咨询订单收到新评价", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_AFTER_SALES_SUBMITTED", descriptor(NotificationCategory.CONSULT, NotificationPriority.HIGH, "售后申请已提交", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_AFTER_SALES_PENDING", descriptor(NotificationCategory.CONSULT, NotificationPriority.HIGH, "咨询订单收到售后申请", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_AFTER_SALES_REJECTED", descriptor(NotificationCategory.CONSULT, NotificationPriority.HIGH, "售后申请未通过", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_REFUNDED", descriptor(NotificationCategory.CONSULT, NotificationPriority.HIGH, "咨询订单已退款", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_PAYMENT_RECONCILED", descriptor(NotificationCategory.CONSULT, NotificationPriority.HIGH, "支付状态已人工确认", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_PAYMENT_EXCEPTION", descriptor(NotificationCategory.CONSULT, NotificationPriority.HIGH, "支付异常订单已处理", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));
        registry.put("CONSULT_REFUND_CONFIRMED", descriptor(NotificationCategory.CONSULT, NotificationPriority.HIGH, "退款处理已确认", "CONSULT_ORDER", "VIEW_CONSULT_ORDER", true));

        // 企业任务使用 taskId 回流，学生和企业端会按角色落到详情或审核工作区。
        registry.put("BOUNTY_SUBMITTED", descriptor(NotificationCategory.BOUNTY, NotificationPriority.HIGH, "悬赏任务有新的成果提交", "BOUNTY_TASK", "VIEW_BOUNTY_TASK", true));
        registry.put("BOUNTY_REVIEWED", descriptor(NotificationCategory.BOUNTY, NotificationPriority.NORMAL, "悬赏任务审核结果已更新", "BOUNTY_TASK", "VIEW_BOUNTY_TASK", true));

        // 认证通知只给出状态入口，具体补件材料仍回到各自资料页处理。
        registry.put("CERTIFICATION_APPROVED", descriptor(NotificationCategory.CERTIFICATION, NotificationPriority.HIGH, "认证审核已通过", "CERTIFICATION", "VIEW_CERTIFICATION_STATUS", true));
        registry.put("CERTIFICATION_REJECTED", descriptor(NotificationCategory.CERTIFICATION, NotificationPriority.HIGH, "认证审核未通过", "CERTIFICATION", "VIEW_CERTIFICATION_STATUS", true));
        registry.put("CERTIFICATION_RESUBMIT_REQUIRED", descriptor(NotificationCategory.CERTIFICATION, NotificationPriority.HIGH, "认证资料需要重新提交", "CERTIFICATION", "VIEW_CERTIFICATION_STATUS", true));

        registry.put("SYSTEM_ANNOUNCEMENT", descriptor(NotificationCategory.SYSTEM, NotificationPriority.NORMAL, "平台公告", "NOTIFICATION_CENTER", "VIEW_NOTIFICATION_CENTER", true));
        registry.put("SYSTEM_MAINTENANCE", descriptor(NotificationCategory.SYSTEM, NotificationPriority.HIGH, "系统维护通知", "NOTIFICATION_CENTER", "VIEW_NOTIFICATION_CENTER", true));
        registry.put("COMMUNITY_POST_REPLIED", descriptor(NotificationCategory.COMMUNITY, NotificationPriority.NORMAL, "你的帖子收到了新回复", "COMMUNITY_POST", "VIEW_COMMUNITY_POST", false));
        registry.put("COMMUNITY_MODERATION_UPDATED", descriptor(NotificationCategory.COMMUNITY, NotificationPriority.HIGH, "你的社区内容审核结果已更新", "COMMUNITY_POST", "VIEW_COMMUNITY_POST", false));
        registry.put("COMMUNITY_REPORT_UPDATED", descriptor(NotificationCategory.COMMUNITY, NotificationPriority.NORMAL, "你的举报处理结果已更新", "COMMUNITY_REPORT", "VIEW_COMMUNITY_REPORTS", false));

        this.descriptors = Map.copyOf(registry);
    }

    public NotificationDescriptor resolve(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return descriptor(NotificationCategory.SYSTEM, NotificationPriority.NORMAL, "平台通知", "NOTIFICATION_CENTER", "VIEW_NOTIFICATION_CENTER", true);
        }
        // 未注册事件降级为系统通知，保证发布链不因新事件漏配而失败。
        return descriptors.getOrDefault(
                eventType.trim(),
                descriptor(NotificationCategory.SYSTEM, NotificationPriority.NORMAL, "平台通知", "NOTIFICATION_CENTER", "VIEW_NOTIFICATION_CENTER", true)
        );
    }

    private static NotificationDescriptor descriptor(
            NotificationCategory category,
            NotificationPriority priority,
            String title,
            String refType,
            String actionCode,
            boolean emailEnabledByDefault
    ) {
        return new NotificationDescriptor(category, priority, title, refType, actionCode, emailEnabledByDefault);
    }

    /**
     * 描述单个通知类型的默认行为。
     */
    public record NotificationDescriptor(
            NotificationCategory category,
            NotificationPriority priority,
            String title,
            String refType,
            String actionCode,
            boolean emailEnabledByDefault
    ) {
    }
}
