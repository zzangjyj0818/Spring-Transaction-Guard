package io.github.zzangjyj0818.transactionguard.spring.transaction;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/** Stores a guard context as a Spring-managed transaction resource. */
public final class TransactionGuardContextRegistry {

    private static final Object RESOURCE_KEY = TransactionGuardContextRegistry.class.getName() + ".CONTEXT";

    /** Creates a transaction context registry. */
    public TransactionGuardContextRegistry() {
    }

    /**
     * Returns the context bound to the current transaction.
     *
     * @return current context, or empty when none is bound
     */
    public Optional<TransactionGuardContext> currentContext() {
        Object resource = TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        return resource instanceof TransactionGuardContext context ? Optional.of(context) : Optional.empty();
    }

    boolean bindIfAbsent(TransactionGuardContext context) {
        if (TransactionSynchronizationManager.hasResource(RESOURCE_KEY)) {
            return false;
        }
        TransactionSynchronizationManager.bindResource(RESOURCE_KEY, context);
        return true;
    }

    void unbindIfCurrent(TransactionGuardContext expected) {
        Object current = TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (current == expected) {
            TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
        }
    }

    void rebindAfterResume(TransactionGuardContext context) {
        Optional<TransactionGuardContext> current = currentContext();
        if (current.filter(bound -> bound == context).isPresent()) {
            return;
        }
        if (current.isPresent()) {
            throw new IllegalStateException("A different transaction guard context is already bound");
        }
        TransactionSynchronizationManager.bindResource(RESOURCE_KEY, context);
    }
}
