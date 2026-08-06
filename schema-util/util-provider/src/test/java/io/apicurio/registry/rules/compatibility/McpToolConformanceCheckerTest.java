package io.apicurio.registry.rules.compatibility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for McpToolConformanceChecker.
 */
class McpToolConformanceCheckerTest {

    private McpToolConformanceChecker checker;

    @BeforeEach
    void setUp() {
        checker = new McpToolConformanceChecker();
    }

    @Test
    void testConformsWhenOutputSatisfiesInput() {
        String producerOutput = """
                {
                    "type": "object",
                    "properties": {
                        "results": { "type": "array" },
                        "total": { "type": "integer" }
                    },
                    "required": ["results", "total"]
                }
                """;

        String consumerInput = """
                {
                    "type": "object",
                    "properties": {
                        "results": { "type": "array" }
                    },
                    "required": ["results"]
                }
                """;

        Set<McpToolConformanceDifference> differences = checker.checkConformance(producerOutput, consumerInput);

        assertTrue(differences.isEmpty(), "Producer output declaring more than the consumer needs should conform");
    }

    @Test
    void testMissingRequiredProperty() {
        String producerOutput = """
                {
                    "type": "object",
                    "properties": {
                        "results": { "type": "array" }
                    }
                }
                """;

        String consumerInput = """
                {
                    "type": "object",
                    "properties": {
                        "results": { "type": "array" },
                        "total": { "type": "integer" }
                    },
                    "required": ["results", "total"]
                }
                """;

        Set<McpToolConformanceDifference> differences = checker.checkConformance(producerOutput, consumerInput);

        assertEquals(1, differences.size());
        McpToolConformanceDifference difference = differences.iterator().next();
        assertEquals(McpToolConformanceDifference.Type.MISSING_REQUIRED_PROPERTY, difference.getType());
        assertTrue(difference.getDescription().contains("total"));
    }

    @Test
    void testPropertyTypeMismatch() {
        String producerOutput = """
                {
                    "type": "object",
                    "properties": {
                        "count": { "type": "string" }
                    },
                    "required": ["count"]
                }
                """;

        String consumerInput = """
                {
                    "type": "object",
                    "properties": {
                        "count": { "type": "integer" }
                    },
                    "required": ["count"]
                }
                """;

        Set<McpToolConformanceDifference> differences = checker.checkConformance(producerOutput, consumerInput);

        assertEquals(1, differences.size());
        assertEquals(McpToolConformanceDifference.Type.PROPERTY_TYPE_MISMATCH,
                differences.iterator().next().getType());
    }

    @Test
    void testIntegerOutputSatisfiesNumberInput() {
        String producerOutput = """
                {
                    "type": "object",
                    "properties": {
                        "amount": { "type": "integer" }
                    },
                    "required": ["amount"]
                }
                """;

        String consumerInput = """
                {
                    "type": "object",
                    "properties": {
                        "amount": { "type": "number" }
                    },
                    "required": ["amount"]
                }
                """;

        Set<McpToolConformanceDifference> differences = checker.checkConformance(producerOutput, consumerInput);

        assertTrue(differences.isEmpty(), "An integer output should satisfy a number-typed input");
    }

    @Test
    void testNumberOutputDoesNotSatisfyIntegerInput() {
        // The reverse of the case above: a number is not always a valid integer.
        String producerOutput = """
                {
                    "type": "object",
                    "properties": {
                        "amount": { "type": "number" }
                    },
                    "required": ["amount"]
                }
                """;

        String consumerInput = """
                {
                    "type": "object",
                    "properties": {
                        "amount": { "type": "integer" }
                    },
                    "required": ["amount"]
                }
                """;

        Set<McpToolConformanceDifference> differences = checker.checkConformance(producerOutput, consumerInput);

        assertEquals(1, differences.size());
        assertEquals(McpToolConformanceDifference.Type.PROPERTY_TYPE_MISMATCH,
                differences.iterator().next().getType());
    }

    @Test
    void testEnumNarrowingViolation() {
        String producerOutput = """
                {
                    "type": "object",
                    "properties": {
                        "status": { "type": "string", "enum": ["ok", "error", "pending"] }
                    },
                    "required": ["status"]
                }
                """;

        String consumerInput = """
                {
                    "type": "object",
                    "properties": {
                        "status": { "type": "string", "enum": ["ok", "error"] }
                    },
                    "required": ["status"]
                }
                """;

        Set<McpToolConformanceDifference> differences = checker.checkConformance(producerOutput, consumerInput);

        assertEquals(1, differences.size());
        McpToolConformanceDifference difference = differences.iterator().next();
        assertEquals(McpToolConformanceDifference.Type.PROPERTY_ENUM_MISMATCH, difference.getType());
        assertTrue(difference.getDescription().contains("pending"));
    }

    @Test
    void testEnumSatisfiedWhenProducerIsSubsetOfConsumer() {
        String producerOutput = """
                {
                    "type": "object",
                    "properties": {
                        "status": { "type": "string", "enum": ["ok", "error"] }
                    },
                    "required": ["status"]
                }
                """;

        String consumerInput = """
                {
                    "type": "object",
                    "properties": {
                        "status": { "type": "string", "enum": ["ok", "error", "pending"] }
                    },
                    "required": ["status"]
                }
                """;

        Set<McpToolConformanceDifference> differences = checker.checkConformance(producerOutput, consumerInput);

        assertTrue(differences.isEmpty(),
                "A producer enum that's a subset of the consumer's accepted enum should conform");
    }

    @Test
    void testUndeclaredPropertyIsIgnored() {
        // The producer emits an extra property the consumer's inputSchema doesn't mention at all --
        // not required, so it shouldn't be flagged either way.
        String producerOutput = """
                {
                    "type": "object",
                    "properties": {
                        "results": { "type": "array" },
                        "debugInfo": { "type": "object" }
                    },
                    "required": ["results"]
                }
                """;

        String consumerInput = """
                {
                    "type": "object",
                    "properties": {
                        "results": { "type": "array" }
                    },
                    "required": ["results"]
                }
                """;

        Set<McpToolConformanceDifference> differences = checker.checkConformance(producerOutput, consumerInput);

        assertTrue(differences.isEmpty());
    }

    @Test
    void testParseErrorOnMalformedSchema() {
        Set<McpToolConformanceDifference> differences = checker.checkConformance("not-json", "also-not-json");

        assertEquals(1, differences.size());
        assertEquals(McpToolConformanceDifference.Type.PARSE_ERROR, differences.iterator().next().getType());
    }
}
