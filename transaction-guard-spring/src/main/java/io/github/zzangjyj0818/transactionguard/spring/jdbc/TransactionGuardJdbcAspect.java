package io.github.zzangjyj0818.transactionguard.spring.jdbc;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Objects;

/** Wraps JDBC connections and statements returned by Spring-managed DataSources. */
@Aspect
public final class TransactionGuardJdbcAspect {
    private final TransactionGuardJdbcRecorder recorder;

    public TransactionGuardJdbcAspect(TransactionGuardJdbcRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
    }

    @Around("target(javax.sql.DataSource) && execution(* getConnection(..))")
    public Object wrapConnection(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        return result instanceof Connection connection ? connectionProxy(connection) : result;
    }

    private Connection connectionProxy(Connection target) {
        return (Connection) Proxy.newProxyInstance(target.getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, new ConnectionHandler(target));
    }

    private Statement statementProxy(Statement target) {
        Class<?> primary = target instanceof java.sql.CallableStatement
                ? java.sql.CallableStatement.class
                : target instanceof java.sql.PreparedStatement
                ? java.sql.PreparedStatement.class : Statement.class;
        return (Statement) Proxy.newProxyInstance(target.getClass().getClassLoader(),
                new Class<?>[]{primary}, new StatementHandler(target));
    }

    private final class ConnectionHandler implements InvocationHandler {
        private final Connection target;
        private ConnectionHandler(Connection target) { this.target = target; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                Object result = method.invoke(target, args);
                return result instanceof Statement statement ? statementProxy(statement) : result;
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }

    private final class StatementHandler implements InvocationHandler {
        private final Statement target;
        private StatementHandler(Statement target) { this.target = target; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (!method.getName().startsWith("execute") || !recorder.isTransactionObserved()) {
                return invokeTarget(method, args);
            }
            long startedAt = recorder.nanoTime();
            try {
                Object result = invokeTarget(method, args);
                recorder.record(elapsed(startedAt), false);
                return result;
            } catch (Throwable failure) {
                recorder.record(elapsed(startedAt), true);
                throw failure;
            }
        }

        private Object invokeTarget(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        private long elapsed(long startedAt) {
            return Math.max(0, recorder.nanoTime() - startedAt);
        }
    }
}
