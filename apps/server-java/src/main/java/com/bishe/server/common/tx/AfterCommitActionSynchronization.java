package com.bishe.server.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 提交后动作同步器，统一处理“事务提交后再执行”的轻量回调。
 */
public final class AfterCommitActionSynchronization implements TransactionSynchronization {

    private final Runnable action;

    private AfterCommitActionSynchronization(Runnable action) {
        this.action = action;
    }

    public static void registerOrRun(Runnable action) {
        if (action == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new AfterCommitActionSynchronization(action));
            return;
        }
        action.run();
    }

    @Override
    public void afterCommit() {
        action.run();
    }
}
