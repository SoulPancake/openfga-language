package dev.openfga.language.graph;

/** The type of an edge in the authorization model graph. */
public enum EdgeType {
    /**
     * A direct edge from a type to a relation.
     * <p>For example, {@code define viewer: [user]}.
     */
    DIRECT,

    /**
     * A rewrite edge from an operator node to a relation node.
     * <p>For example, in {@code define rel1: a OR b}, the edge from the OR node to rel1.
     */
    REWRITE,

    /**
     * A tuple-to-userset edge.
     * <p>For example, {@code define viewer: admin from parent}.
     */
    TTU,

    /**
     * A computed edge pointing to another relation.
     * <p>For example, {@code define rel1: rel2}.
     */
    COMPUTED,
}
