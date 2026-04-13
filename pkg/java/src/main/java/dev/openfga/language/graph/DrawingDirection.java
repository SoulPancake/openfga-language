package dev.openfga.language.graph;

/**
 * The logical direction in which the graph was drawn.
 *
 * <p>The direction affects DOT rendering (rankdir) and is toggled by
 * {@link AuthorizationModelGraph#reversed()}.
 */
public enum DrawingDirection {
    /**
     * Terminal types have outgoing edges and no incoming edges.
     * <p>Default for newly built graphs. Suitable for ListObjects analysis.
     */
    LIST_OBJECTS,

    /**
     * Terminal types have incoming edges and no outgoing edges.
     * <p>Suitable for Check analysis.
     */
    CHECK,
}
