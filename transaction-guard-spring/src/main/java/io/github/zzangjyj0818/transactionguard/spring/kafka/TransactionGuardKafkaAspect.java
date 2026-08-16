package io.github.zzangjyj0818.transactionguard.spring.kafka;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** Instruments KafkaTemplate send invocations while preserving the returned future. */
@Aspect
public final class TransactionGuardKafkaAspect {
    private final TransactionGuardKafkaRecorder recorder;

    public TransactionGuardKafkaAspect(TransactionGuardKafkaRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
    }

    @Around("target(org.springframework.kafka.core.KafkaTemplate) && execution(* send*(..))")
    public Object observe(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!recorder.isTransactionObserved()) return joinPoint.proceed();
        long startedAt = recorder.nanoTime();
        try {
            Object result = joinPoint.proceed();
            if (result instanceof CompletionStage<?> completion) {
                completion.whenComplete((ignored, failure) -> {
                    if (failure == null) {
                        recorder.recordSuccess(elapsed(startedAt));
                    } else {
                        recorder.recordFailure(elapsed(startedAt), unwrap(failure));
                    }
                });
            } else {
                recorder.recordSuccess(elapsed(startedAt));
            }
            return result;
        } catch (Throwable failure) {
            recorder.recordFailure(elapsed(startedAt), failure);
            throw failure;
        }
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
    }

    private long elapsed(long startedAt) {
        return Math.max(0, recorder.nanoTime() - startedAt);
    }
}
