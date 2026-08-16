package io.github.zzangjyj0818.transactionguard.spring.aop;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionEntryPoint;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionObservation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Objects;

/** Captures transactional method entry points and triggers transaction observation registration. */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
public final class TransactionGuardAspect {

    private final TransactionObservation observation;

    /**
     * Creates a transaction guard aspect.
     *
     * @param observation transaction registration component
     */
    public TransactionGuardAspect(TransactionObservation observation) {
        this.observation = Objects.requireNonNull(observation, "observation must not be null");
    }

    /**
     * Observes method- or class-level transactional invocations.
     *
     * @param joinPoint intercepted transactional invocation
     * @return original invocation result
     * @throws Throwable when the original invocation fails
     */
    @Around("execution(* *(..)) && ("
            + "@annotation(org.springframework.transaction.annotation.Transactional) || "
            + "@within(org.springframework.transaction.annotation.Transactional))")
    public Object observe(ProceedingJoinPoint joinPoint) throws Throwable {
        Class<?> targetClass = joinPoint.getTarget() == null
                ? joinPoint.getSignature().getDeclaringType()
                : AopUtils.getTargetClass(joinPoint.getTarget());
        observation.observe(new TransactionEntryPoint(
                targetClass.getName(),
                joinPoint.getSignature().getName(),
                joinPoint.getSignature().toLongString()
        ));
        return joinPoint.proceed();
    }
}
