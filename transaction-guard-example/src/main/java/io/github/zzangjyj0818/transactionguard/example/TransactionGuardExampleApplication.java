package io.github.zzangjyj0818.transactionguard.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Runnable application demonstrating Transaction Guard policies and supported HTTP clients. */
@SpringBootApplication
public class TransactionGuardExampleApplication {

    private TransactionGuardExampleApplication() {
    }

    /**
     * Starts the example application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(TransactionGuardExampleApplication.class, args);
    }
}
