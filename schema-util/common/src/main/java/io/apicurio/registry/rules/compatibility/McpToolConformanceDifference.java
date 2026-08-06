package io.apicurio.registry.rules.compatibility;

import java.util.Objects;

/**
 * A single reason {@link McpToolConformanceChecker} found that a producer tool's output does not
 * satisfy a consumer tool's input.
 * <p>
 * Deliberately not a {@link CompatibilityDifference} -- that interface's {@code asRuleViolation()}
 * contract is for compatibility rule violations between two versions of the same artifact, raised
 * during a write. Conformance between two different tools isn't a write-time rule violation; it's
 * a read-time query a "Compatible Tools" endpoint would run to explain why a candidate matched or
 * didn't, so this only needs {@code type}/{@code description}, not a {@code RuleViolation} mapping.
 */
public class McpToolConformanceDifference {

    public enum Type {
        MISSING_REQUIRED_PROPERTY,
        PROPERTY_TYPE_MISMATCH,
        PROPERTY_ENUM_MISMATCH,
        PARSE_ERROR
    }

    private final Type type;
    private final String description;

    public McpToolConformanceDifference(Type type, String description) {
        this.type = Objects.requireNonNull(type);
        this.description = Objects.requireNonNull(description);
    }

    public Type getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        McpToolConformanceDifference that = (McpToolConformanceDifference) o;
        return type == that.type && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, description);
    }

    @Override
    public String toString() {
        return "McpToolConformanceDifference{" + "type=" + type + ", description='" + description
                + '\'' + '}';
    }
}
