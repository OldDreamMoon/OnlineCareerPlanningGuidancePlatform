package com.bishe.server.consult.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 咨询订单维护任务：定期回收超时未支付订单。
 */
@Component
public class ConsultOrderMaintenanceJob {

    private final ConsultService consultService;
    private final ConsultAfterSalesService consultAfterSalesService;

    public ConsultOrderMaintenanceJob(ConsultService consultService, ConsultAfterSalesService consultAfterSalesService) {
        this.consultService = consultService;
        this.consultAfterSalesService = consultAfterSalesService;
    }

    @Scheduled(fixedDelayString = "${payment.unpaid-reclaim-fixed-delay-ms:60000}")
    public void reconcileTimedOutOrders() {
        // 未支付订单回收也会在列表/详情读取时懒执行，定时任务负责后台兜底。
        consultService.reconcileTimedOutPendingOrders();
    }

    @Scheduled(fixedDelayString = "${consult.mentor-reply-reclaim-fixed-delay-ms:60000}")
    public void reconcileTimedOutMentorReplies() {
        // 导师超时未答的售后退款链由售后服务统一编排。
        consultAfterSalesService.reconcileTimedOutMentorReplyOrders();
    }
}
