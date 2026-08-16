package io.github.zzangjyj0818.transactionguard.spring.kafka;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.Objects;

/** Instruments KafkaTemplate send invocations while preserving the returned future. */
@Aspect
public final class TransactionGuardKafkaAspect {
    private final TransactionGuardKafkaRecorder recorder;

    public TransactionGuardKafkaAspect(TransactionGuardKafkaRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
    }

    @Around("execution(* org.springframework.kafka.core.KafkaTemplate.send*(..))")
    public Object observe(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!recorder.isTransactionObserved()) return joinPoint.proceed();
        long startedAt = recorder.nanoTime();
        try {
            Object result = joinPoint.proceed();
            recorder.recordSuccess(elapsed(startedAt));
            return result;
        } catch (Throwable failure) {
            recorder.recordFailure(elapsed(startedAt), failure);
            throw failure;
        }
    }

    private long elapsed(long startedAt) {
        return Math.max(0, recorder.nanoTime() - startedAt);
    }
}
