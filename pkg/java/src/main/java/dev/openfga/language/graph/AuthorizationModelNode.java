package dev.openfga.language.graph;

/**
 * A node in the {@link AuthorizationModelGraph}.
 *
 * <p>Each node has a unique label (used for lookups), a display label,
 * and a {@link NodeType}.
 */
public final class AuthorizationModelNode {
    private final long id;
    private final String label;
    private final NodeType nodeType;
    private final String uniqueLabel;

    AuthorizationModelNode(long id, String uniqueLabel, String label, NodeType nodeType) {
        this.id = id;
        this.uniqueLabel = uniqueLabel;
        this.label = label;
        this.nodeType = nodeType;
    }

    /** Internal numeric identifier. */
    public long getId() {
        return id;
    }

    /** Display label, e.g. {@code "group#member"} or {@code "union"}. */
    public String getLabel() {
        return label;
    }

    /** Node classification. */
    public NodeType getNodeType() {
        return nodeType;
    }

    /** Unique label used for O(1) lookup. Same as {@link #getLabel()} for non-operator nodes. */
    public String getUniqueLabel() {
        return uniqueLabel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthorizationModelNode)) return false;
        AuthorizationModelNode that = (AuthorizationModelNode) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return uniqueLabel;
    }
}
