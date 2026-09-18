package com.dq.example;

import com.dq.engine.model.Rule;
import com.dq.example.json.JsonRuleCatalog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Supplies the rule catalog. A production host swaps {@code fromClasspath} for a MariaDB
 * query behind a Redis cache and nothing else changes, because the engine only wants a
 * {@code List<Rule>}.
 *
 * <p>Loading happens at startup, so a malformed expression stops the application from
 * starting, naming the offending rule.
 */
@Configuration
public class CatalogConfiguration {

    @Bean
    public List<Rule> ruleCatalog() {
        return JsonRuleCatalog.fromClasspath("/rules.json");
    }
}
