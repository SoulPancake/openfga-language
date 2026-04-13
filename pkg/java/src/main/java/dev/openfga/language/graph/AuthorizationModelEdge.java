package dev.openfga.language.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An edge (line) in the {@link AuthorizationModelGraph}.
 *
 * <p>Because the graph is a multigraph, there can be multiple edges between
 * the same pair of nodes (e.g. different conditions on the same direct edge).
 */
public final class AuthorizationModelEdge {
    private final long id;
    private final AuthorizationModelNode from;
    private final AuthorizationModelNode to;
    private final EdgeType edgeType;
    private final String tuplesetRelation;
    private final List<String> conditions;

    AuthorizationModelEdge(
            long id,
            AuthorizationModelNode from,
            AuthorizationModelNode to,
            EdgeType edgeType,
            String tuplesetRelation,
            List<String> conditions) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.edgeType = edgeType;
        this.tuplesetRelation = tuplesetRelation;
        this.conditions = new ArrayList<>(conditions);
    }

    /** Internal numeric identifier. */
    public long getId() {
        return id;
    }

    /** Source node. */
    public AuthorizationModelNode getFrom() {
        return from;
    }

    /** Target node. */
    public AuthorizationModelNode getTo() {
        return to;
    }

    /** Edge classification. */
    public EdgeType getEdgeType() {
        return edgeType;
    }

    /**
     * The TTU relation label, e.g. {@code "document#parent"}.
     * Only meaningful when {@link #getEdgeType()} is {@link EdgeType#TTU}.
     */
    public String getTuplesetRelation() {
        return tuplesetRelation;
    }

    /** Conditions on this edge (empty string means unconditional). */
    public List<String> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    /** Package-private: append a condition for de-duplication. */
    void addCondition(String condition) {
        conditions.add(condition);
    }
}
