package dev.raghav.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootApplication
public class PaymentsApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentsApplication.class, args);
    }

    @Bean
    @Profile("!postgres")
    LedgerService ledgerService() {
        return new LedgerService();
    }
    @Bean
    @Profile("postgres")
    LedgerService postgresLedgerService(JdbcTemplate jdbc, PlatformTransactionManager tx) {
        return new PostgresLedgerService(jdbc, new TransactionTemplate(tx));
    }
}
