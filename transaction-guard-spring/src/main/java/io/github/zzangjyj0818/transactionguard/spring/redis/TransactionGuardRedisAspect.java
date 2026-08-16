package io.github.zzangjyj0818.transactionguard.spring.redis;

import io.github.zzangjyj0818.transactionguard.core.model.RedisCommandCategory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.Locale;
import java.util.Objects;

/** Instruments imperative Spring Data Redis operations without retaining arguments. */
@Aspect
public final class TransactionGuardRedisAspect {

    private final TransactionGuardRedisRecorder recorder;

    public TransactionGuardRedisAspect(TransactionGuardRedisRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
    }

    @Around("execution(* org.springframework.data.redis.core.*Operations.*(..))"
            + " || execution(* org.springframework.data.redis.core.RedisTemplate.execute*(..))")
    public Object observe(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!recorder.isTransactionObserved()) {
            return joinPoint.proceed();
        }
        RedisCommandCategory category = category(joinPoint.getSignature().getName());
        long startedAt = recorder.nanoTime();
        try {
            Object result = joinPoint.proceed();
            recorder.recordSuccess(category, elapsed(startedAt));
            return result;
        } catch (Throwable failure) {
            recorder.recordFailure(category, elapsed(startedAt), failure);
            throw failure;
        }
    }

    private long elapsed(long startedAt) {
        return Math.max(0, recorder.nanoTime() - startedAt);
    }

    static RedisCommandCategory category(String methodName) {
        String name = methodName.toLowerCase(Locale.ROOT);
        if (name.contains("delete") || name.contains("unlink") || name.contains("remove")) {
            return RedisCommandCategory.DELETE;
        }
        if (name.contains("multi") || name.contains("exec") || name.contains("batch")) {
            return RedisCommandCategory.BATCH;
        }
        if (name.contains("execute")) return RedisCommandCategory.SCRIPT;
        if (name.startsWith("get") || name.startsWith("has") || name.startsWith("size")
                || name.startsWith("count") || name.startsWith("range") || name.startsWith("scan")
                || name.startsWith("random") || name.startsWith("members") || name.startsWith("entries")) {
            return RedisCommandCategory.READ;
        }
        if (name.startsWith("set") || name.startsWith("put") || name.startsWith("add")
                || name.startsWith("increment") || name.startsWith("decrement")
                || name.startsWith("expire") || name.startsWith("rename") || name.startsWith("move")) {
            return RedisCommandCategory.WRITE;
        }
        return RedisCommandCategory.OTHER;
    }
}
