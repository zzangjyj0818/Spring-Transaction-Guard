package io.github.zzangjyj0818.transactionguard.core.model;

/** Privacy-safe Redis command categories exposed to policies and reporters. */
public enum RedisCommandCategory {
    READ,
    WRITE,
    DELETE,
    BATCH,
    SCRIPT,
    OTHER
}
