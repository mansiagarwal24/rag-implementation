package com.testcase.rag_implement;

import com.testcase.rag_implement.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Verifies the full application context boots against a real pgvector database with
 * Flyway migrations applied and model calls stubbed (no API key required).
 */
class RagImplementApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
