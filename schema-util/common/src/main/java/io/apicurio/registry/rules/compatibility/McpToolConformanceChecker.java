package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Checks whether one MCP tool's {@code outputSchema} structurally satisfies another tool's
 * {@code inputSchema} -- i.e. whether Tool A's output can safely feed Tool B's input.
 * <p>
 * This answers a different question than {@link McpToolCompatibilityChecker}, which only compares
 * two versions of the <em>same</em> tool over time (a symmetric diff: did this property change).
 * Matching a producer's output against a consumer's input is one directional: does the producer's
 * shape satisfy what the consumer requires. It doesn't reuse {@code McpToolCompatibilityChecker}
 * for that reason, and doesn't implement {@link CompatibilityChecker} either -- that interface's
 * contract (existing vs proposed content of one artifact) doesn't fit two different tools being
 * compared against each other.
 * <p>
 * The per property comparison here follows the same shape #8662 added to
 * {@code McpToolCompatibilityChecker} (itself modeled on {@code PromptTemplateCompatibilityChecker}),
 * just pointed one direction: a property's type and enum are checked against what the
 * <em>consumer</em> declares it needs, not against a prior version of the same schema.
 */
public class McpToolConformanceChecker {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param producerOutputSchema the {@code outputSchema} of the tool that would produce data
     * @param consumerInputSchema  the {@code inputSchema} of the tool that would consume it
     * @return the reasons the producer's output does not satisfy the consumer's input; empty if it does
     */
    public Set<McpToolConformanceDifference> checkConformance(String producerOutputSchema,
            String consumerInputSchema) {
        Set<McpToolConformanceDifference> differences = new HashSet<>();

        JsonNode producer;
        JsonNode consumer;
        try {
            producer = mapper.readTree(producerOutputSchema);
            consumer = mapper.readTree(consumerInputSchema);
        } catch (Exception e) {
            differences.add(new McpToolConformanceDifference(McpToolConformanceDifference.Type.PARSE_ERROR,
                    "Failed to parse schema: " + e.getMessage()));
            return differences;
        }

        JsonNode producerProps = properties(producer);
        JsonNode consumerProps = properties(consumer);

        for (String requiredParam : requiredParams(consumer)) {
            if (producerProps == null || !producerProps.has(requiredParam)) {
                differences.add(new McpToolConformanceDifference(
                        McpToolConformanceDifference.Type.MISSING_REQUIRED_PROPERTY,
                        "Consumer requires '" + requiredParam
                                + "' but the producer's output does not declare it"));
            }
        }

        if (producerProps != null && consumerProps != null) {
            Iterator<String> consumerPropertyNames = consumerProps.fieldNames();
            while (consumerPropertyNames.hasNext()) {
                String propertyName = consumerPropertyNames.next();
                if (!producerProps.has(propertyName)) {
                    continue; // producer just doesn't emit this property -- fine unless it was required, checked above
                }

                JsonNode producerProperty = producerProps.get(propertyName);
                JsonNode consumerProperty = consumerProps.get(propertyName);
                checkTypeSatisfies(propertyName, producerProperty, consumerProperty, differences);
                checkEnumSatisfies(propertyName, producerProperty, consumerProperty, differences);
            }
        }

        return differences;
    }

    /**
     * A producer type satisfies a consumer type if they're equal, or if the producer's type is a
     * JSON Schema subtype of the consumer's. Currently that's just {@code integer} into
     * {@code number}: every integer is a valid number, so integer output satisfies a
     * number-typed input, but the reverse doesn't hold -- a non-integral number would not satisfy
     * an integer-only field.
     */
    private void checkTypeSatisfies(String propertyName, JsonNode producerProperty, JsonNode consumerProperty,
            Set<McpToolConformanceDifference> differences) {
        String producerType = textOrNull(producerProperty.get("type"));
        String consumerType = textOrNull(consumerProperty.get("type"));
        if (producerType == null || consumerType == null || producerType.equals(consumerType)) {
            return;
        }
        if ("integer".equals(producerType) && "number".equals(consumerType)) {
            return;
        }

        differences.add(new McpToolConformanceDifference(McpToolConformanceDifference.Type.PROPERTY_TYPE_MISMATCH,
                "Property '" + propertyName + "' is '" + producerType + "' in the producer's output but '"
                        + consumerType + "' is required by the consumer's input"));
    }

    /**
     * If the consumer restricts a property to an enum, every value the producer's own enum for
     * that property declares must be inside the consumer's accepted set. A producer property with
     * no declared enum ("could emit anything of that type") can't be checked statically against a
     * consumer enum, so it's left unflagged here -- that's a runtime concern, not a structural one.
     */
    private void checkEnumSatisfies(String propertyName, JsonNode producerProperty, JsonNode consumerProperty,
            Set<McpToolConformanceDifference> differences) {
        JsonNode consumerEnum = consumerProperty.get("enum");
        JsonNode producerEnum = producerProperty.get("enum");
        if (consumerEnum == null || !consumerEnum.isArray() || producerEnum == null || !producerEnum.isArray()) {
            return;
        }

        Set<String> allowedByConsumer = new HashSet<>();
        for (JsonNode value : consumerEnum) {
            allowedByConsumer.add(value.asText());
        }

        for (JsonNode value : producerEnum) {
            if (!allowedByConsumer.contains(value.asText())) {
                differences.add(new McpToolConformanceDifference(
                        McpToolConformanceDifference.Type.PROPERTY_ENUM_MISMATCH,
                        "Property '" + propertyName + "' producer value '" + value.asText()
                                + "' is not in the consumer's accepted enum"));
                return;
            }
        }
    }

    private static JsonNode properties(JsonNode schema) {
        JsonNode props = schema.get("properties");
        return (props != null && props.isObject()) ? props : null;
    }

    private static Set<String> requiredParams(JsonNode schema) {
        Set<String> required = new HashSet<>();
        JsonNode requiredNode = schema.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            for (JsonNode item : requiredNode) {
                if (item.isTextual()) {
                    required.add(item.asText());
                }
            }
        }
        return required;
    }

    private static String textOrNull(JsonNode node) {
        return (node != null && node.isTextual()) ? node.asText() : null;
    }
}
