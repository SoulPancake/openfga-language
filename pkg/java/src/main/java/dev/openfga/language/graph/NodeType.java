package dev.openfga.language.graph;

/** The type of a node in the authorization model graph. */
public enum NodeType {
    /** A concrete type, e.g. {@code group}. */
    SPECIFIC_TYPE,

    /** A type-and-relation pair, e.g. {@code group#viewer}. */
    SPECIFIC_TYPE_AND_RELATION,

    /** A set-operator node (union, intersection, or exclusion). */
    OPERATOR,

    /** A wildcard type, e.g. {@code group:*}. */
    SPECIFIC_TYPE_WILDCARD,
}
