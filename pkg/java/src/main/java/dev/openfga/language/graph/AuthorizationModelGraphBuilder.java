package dev.openfga.language.graph;

import dev.openfga.sdk.api.model.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds an {@link AuthorizationModelGraph} from an {@link AuthorizationModel}.
 *
 * <p>Example usage:
 * <pre>{@code
 * AuthorizationModel model = ...; // from DSL or JSON
 * AuthorizationModelGraph graph = AuthorizationModelGraphBuilder.build(model);
 * boolean reachable = graph.pathExists("user", "document#viewer");
 * }</pre>
 */
public final class AuthorizationModelGraphBuilder {

    // Unconditional condition sentinel (mirrors Go's NoCond = "").
    private static final String NO_COND = "";

    private final Map<String, AuthorizationModelNode> nodesByLabel = new LinkedHashMap<>();
    private final Map<Long, AuthorizationModelNode> nodesById = new LinkedHashMap<>();
    private final Map<Long, List<AuthorizationModelEdge>> outEdges = new LinkedHashMap<>();
    private final Map<Long, List<AuthorizationModelEdge>> inEdges = new LinkedHashMap<>();
    private final AtomicLong nodeIdSeq = new AtomicLong();
    private final AtomicLong edgeIdSeq = new AtomicLong();

    private AuthorizationModelGraphBuilder() {}

    /**
     * Builds an {@link AuthorizationModelGraph} from an OpenFGA authorization model.
     *
     * <p>Types ({@code group}), usersets ({@code group#member}), wildcards ({@code group:*})
     * and operators (union/intersection/exclusion) are encoded as nodes. By default the
     * graph is drawn bottom→top (terminal types have outgoing edges).
     *
     * @param model the authorization model to convert
     * @return a new graph
     */
    public static AuthorizationModelGraph build(AuthorizationModel model) {
        AuthorizationModelGraphBuilder builder = new AuthorizationModelGraphBuilder();
        builder.parseModel(model);
        return new AuthorizationModelGraph(
                builder.nodesByLabel,
                builder.nodesById,
                builder.outEdges,
                builder.inEdges,
                DrawingDirection.LIST_OBJECTS);
    }

    // ---- model parsing ----

    private void parseModel(AuthorizationModel model) {
        List<TypeDefinition> typeDefs = model.getTypeDefinitions();
        if (typeDefs == null) return;

        // Sort type definitions by name for stable output.
        List<TypeDefinition> sorted = new ArrayList<>(typeDefs);
        sorted.sort(Comparator.comparing(TypeDefinition::getType));

        for (TypeDefinition typeDef : sorted) {
            getOrAddNode(typeDef.getType(), typeDef.getType(), NodeType.SPECIFIC_TYPE);

            Map<String, Userset> relations = typeDef.getRelations();
            if (relations == null) continue;

            List<String> sortedRelations = new ArrayList<>(relations.keySet());
            Collections.sort(sortedRelations);

            for (String relation : sortedRelations) {
                String uniqueLabel = typeDef.getType() + "#" + relation;
                AuthorizationModelNode parentNode =
                        getOrAddNode(uniqueLabel, uniqueLabel, NodeType.SPECIFIC_TYPE_AND_RELATION);
                Userset rewrite = relations.get(relation);
                processRewrite(parentNode, model, rewrite, typeDef, relation);
            }
        }
    }

    private void processRewrite(
            AuthorizationModelNode parentNode,
            AuthorizationModel model,
            Userset rewrite,
            TypeDefinition typeDef,
            String relation) {
        if (rewrite == null) return;

        String operator;
        List<Userset> children;

        if (rewrite.getThis() != null) {
            parseThis(parentNode, typeDef, relation);
            return;
        } else if (rewrite.getComputedUserset() != null) {
            parseComputed(parentNode, typeDef, rewrite.getComputedUserset().getRelation());
            return;
        } else if (rewrite.getTupleToUserset() != null) {
            parseTupleToUserset(parentNode, model, typeDef, rewrite.getTupleToUserset());
            return;
        } else if (rewrite.getUnion() != null) {
            operator = Operators.UNION;
            children = rewrite.getUnion().getChild();
        } else if (rewrite.getIntersection() != null) {
            operator = Operators.INTERSECTION;
            children = rewrite.getIntersection().getChild();
        } else if (rewrite.getDifference() != null) {
            operator = Operators.EXCLUSION;
            children = Arrays.asList(
                    rewrite.getDifference().getBase(), rewrite.getDifference().getSubtract());
        } else {
            return;
        }

        String operatorNodeLabel = operator + ":" + UUID.randomUUID();
        AuthorizationModelNode operatorNode = getOrAddNode(operatorNodeLabel, operator, NodeType.OPERATOR);

        addEdge(operatorNode, parentNode, EdgeType.REWRITE, "", null);
        if (children != null) {
            for (Userset child : children) {
                processRewrite(operatorNode, model, child, typeDef, relation);
            }
        }
    }

    private void parseThis(AuthorizationModelNode parentNode, TypeDefinition typeDef, String relation) {
        List<RelationReference> directlyRelated = getDirectlyRelatedTypes(typeDef, relation);
        for (RelationReference ref : directlyRelated) {
            AuthorizationModelNode curNode = null;

            boolean hasRelation =
                    ref.getRelation() != null && !ref.getRelation().isEmpty();
            boolean hasWildcard = ref.getWildcard() != null;

            if (!hasRelation && !hasWildcard) {
                // direct assignment to concrete type
                String assignableType = ref.getType();
                curNode = getOrAddNode(assignableType, assignableType, NodeType.SPECIFIC_TYPE);
            }

            if (hasWildcard) {
                // direct assignment to wildcard
                String assignableWildcard = ref.getType() + ":*";
                curNode = getOrAddNode(assignableWildcard, assignableWildcard, NodeType.SPECIFIC_TYPE_WILDCARD);
            }

            if (hasRelation) {
                // direct assignment to userset
                String assignableUserset = ref.getType() + "#" + ref.getRelation();
                curNode = getOrAddNode(assignableUserset, assignableUserset, NodeType.SPECIFIC_TYPE_AND_RELATION);
            }

            if (curNode != null) {
                upsertEdge(curNode, parentNode, EdgeType.DIRECT, "", ref.getCondition());
            }
        }
    }

    private void parseComputed(AuthorizationModelNode parentNode, TypeDefinition typeDef, String relation) {
        String rewrittenNodeName = typeDef.getType() + "#" + relation;
        AuthorizationModelNode newNode =
                getOrAddNode(rewrittenNodeName, rewrittenNodeName, NodeType.SPECIFIC_TYPE_AND_RELATION);

        EdgeType edgeType = EdgeType.REWRITE;
        if (parentNode.getNodeType() == NodeType.SPECIFIC_TYPE_AND_RELATION
                && newNode.getNodeType() == NodeType.SPECIFIC_TYPE_AND_RELATION) {
            edgeType = EdgeType.COMPUTED;
        }
        addEdge(newNode, parentNode, edgeType, "", null);
    }

    private void parseTupleToUserset(
            AuthorizationModelNode parentNode, AuthorizationModel model, TypeDefinition typeDef, TupleToUserset ttu) {
        String tuplesetRelation = ttu.getTupleset().getRelation();
        String computedRelation = ttu.getComputedUserset().getRelation();

        List<RelationReference> directlyRelated = getDirectlyRelatedTypes(typeDef, tuplesetRelation);

        for (RelationReference relatedType : directlyRelated) {
            String tuplesetType = relatedType.getType();

            if (!typeAndRelationExists(model, tuplesetType, computedRelation)) {
                continue;
            }

            String rewrittenNodeName = tuplesetType + "#" + computedRelation;
            AuthorizationModelNode nodeSource =
                    getOrAddNode(rewrittenNodeName, rewrittenNodeName, NodeType.SPECIFIC_TYPE_AND_RELATION);
            String typeTuplesetRelation = typeDef.getType() + "#" + tuplesetRelation;

            if (hasEdge(nodeSource, parentNode, EdgeType.TTU, typeTuplesetRelation)) {
                continue;
            }

            upsertEdge(nodeSource, parentNode, EdgeType.TTU, typeTuplesetRelation, relatedType.getCondition());
        }
    }

    // ---- helpers ----

    private AuthorizationModelNode getOrAddNode(String uniqueLabel, String label, NodeType nodeType) {
        AuthorizationModelNode existing = nodesByLabel.get(uniqueLabel);
        if (existing != null) {
            return existing;
        }
        long id = nodeIdSeq.getAndIncrement();
        AuthorizationModelNode node = new AuthorizationModelNode(id, uniqueLabel, label, nodeType);
        nodesByLabel.put(uniqueLabel, node);
        nodesById.put(id, node);
        return node;
    }

    private AuthorizationModelEdge addEdge(
            AuthorizationModelNode from,
            AuthorizationModelNode to,
            EdgeType edgeType,
            String tuplesetRelation,
            List<String> conditions) {
        if (from == null || to == null) return null;
        if (conditions == null || conditions.isEmpty()) {
            conditions = Collections.singletonList(NO_COND);
        }
        long id = edgeIdSeq.getAndIncrement();
        AuthorizationModelEdge edge = new AuthorizationModelEdge(id, from, to, edgeType, tuplesetRelation, conditions);
        outEdges.computeIfAbsent(from.getId(), k -> new ArrayList<>()).add(edge);
        inEdges.computeIfAbsent(to.getId(), k -> new ArrayList<>()).add(edge);
        return edge;
    }

    private void upsertEdge(
            AuthorizationModelNode from,
            AuthorizationModelNode to,
            EdgeType edgeType,
            String tuplesetRelation,
            String condition) {
        if (from == null || to == null) return;

        List<AuthorizationModelEdge> edges = outEdges.getOrDefault(from.getId(), Collections.emptyList());
        for (AuthorizationModelEdge edge : edges) {
            if (edge.getTo().getId() == to.getId()
                    && edge.getEdgeType() == edgeType
                    && Objects.equals(edge.getTuplesetRelation(), tuplesetRelation)) {
                // Check if condition already exists
                String cond = (condition != null) ? condition : NO_COND;
                if (!edge.getConditions().contains(cond)) {
                    edge.addCondition(cond);
                }
                return;
            }
        }

        String cond = (condition != null) ? condition : NO_COND;
        addEdge(from, to, edgeType, tuplesetRelation, Collections.singletonList(cond));
    }

    private boolean hasEdge(
            AuthorizationModelNode from, AuthorizationModelNode to, EdgeType edgeType, String tuplesetRelation) {
        if (from == null || to == null) return false;

        for (AuthorizationModelEdge edge : outEdges.getOrDefault(from.getId(), Collections.emptyList())) {
            if (edge.getTo().getId() == to.getId()
                    && edge.getEdgeType() == edgeType
                    && Objects.equals(edge.getTuplesetRelation(), tuplesetRelation)) {
                return true;
            }
        }
        return false;
    }

    private static List<RelationReference> getDirectlyRelatedTypes(TypeDefinition typeDef, String relation) {
        if (typeDef.getMetadata() == null || typeDef.getMetadata().getRelations() == null) {
            return Collections.emptyList();
        }
        RelationMetadata rm = typeDef.getMetadata().getRelations().get(relation);
        if (rm == null || rm.getDirectlyRelatedUserTypes() == null) {
            return Collections.emptyList();
        }
        return rm.getDirectlyRelatedUserTypes();
    }

    private static boolean typeAndRelationExists(AuthorizationModel model, String typeName, String relation) {
        List<TypeDefinition> typeDefs = model.getTypeDefinitions();
        if (typeDefs == null) return false;
        for (TypeDefinition td : typeDefs) {
            if (typeName.equals(td.getType())) {
                Map<String, Userset> relations = td.getRelations();
                return relations != null && relations.containsKey(relation);
            }
        }
        return false;
    }
}
