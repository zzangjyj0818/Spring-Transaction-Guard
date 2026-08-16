package io.github.zzangjyj0818.transactionguard.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** HTTP entry points for the three reproducible risk scenarios and their local remote stubs. */
@RestController
@RequestMapping
public class TransactionRiskController {

    private final TransactionRiskScenarios scenarios;

    /**
     * Creates the example controller.
     *
     * @param scenarios transactional risk scenarios
     */
    public TransactionRiskController(TransactionRiskScenarios scenarios) {
        this.scenarios = scenarios;
    }

    /**
     * Runs the TG001 example.
     *
     * @return scenario result
     */
    @GetMapping("/guard/tg001")
    public String tg001() {
        return scenarios.longTransaction();
    }

    /**
     * Runs the TG002 example.
     *
     * @return scenario result
     */
    @GetMapping("/guard/tg002")
    public String tg002() {
        return scenarios.externalCallInTransaction();
    }

    /**
     * Runs the TG003 example.
     *
     * @return scenario result
     */
    @GetMapping("/guard/tg003")
    public String tg003() {
        return scenarios.slowExternalCallInTransaction();
    }

    /**
     * Simulates a fast downstream endpoint.
     *
     * @return simulated response
     */
    @GetMapping("/remote/fast")
    public String fastRemote() {
        return "fast response";
    }

    /**
     * Simulates a slow downstream endpoint.
     *
     * @return simulated response
     */
    @GetMapping("/remote/slow")
    public String slowRemote() {
        pause(Duration.ofMillis(200));
        return "slow response";
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Remote simulation was interrupted", exception);
        }
    }
}
