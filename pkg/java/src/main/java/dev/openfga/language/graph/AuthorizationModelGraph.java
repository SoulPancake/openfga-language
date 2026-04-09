package dev.openfga.language.graph;

import java.util.*;

/**
 * A directed multigraph representation of an OpenFGA authorization model.
 *
 * <p>Types ({@code group}), usersets ({@code group#member}), wildcards ({@code group:*}),
 * and set operators (union/intersection/exclusion) are encoded as nodes.
 * Edges represent the relationships between them.
 *
 * <p>Build an instance via {@link AuthorizationModelGraphBuilder#build}.
 */
public final class AuthorizationModelGraph {

    private final Map<String, AuthorizationModelNode> nodesByLabel;
    private final Map<Long, AuthorizationModelNode> nodesById;
    // adjacency: fromId -> list of edges originating there
    private final Map<Long, List<AuthorizationModelEdge>> outEdges;
    // reverse adjacency for reversed() building
    private final Map<Long, List<AuthorizationModelEdge>> inEdges;

    private final DrawingDirection drawingDirection;

    AuthorizationModelGraph(
            Map<String, AuthorizationModelNode> nodesByLabel,
            Map<Long, AuthorizationModelNode> nodesById,
            Map<Long, List<AuthorizationModelEdge>> outEdges,
            Map<Long, List<AuthorizationModelEdge>> inEdges,
            DrawingDirection drawingDirection) {
        this.nodesByLabel = nodesByLabel;
        this.nodesById = nodesById;
        this.outEdges = outEdges;
        this.inEdges = inEdges;
        this.drawingDirection = drawingDirection;
    }

    /** Returns the logical drawing direction of this graph. */
    public DrawingDirection getDrawingDirection() {
        return drawingDirection;
    }

    /**
     * O(1) lookup of a node by its unique label.
     *
     * @throws GraphException if the node is not found
     */
    public AuthorizationModelNode getNodeByLabel(String label) throws GraphException {
        AuthorizationModelNode node = nodesByLabel.get(label);
        if (node == null) {
            throw new GraphException("node with label " + label + " not found");
        }
        return node;
    }

    /** Returns all nodes in the graph. */
    public Collection<AuthorizationModelNode> getNodes() {
        return Collections.unmodifiableCollection(nodesById.values());
    }

    /** Returns all outgoing edges from the given node. */
    public List<AuthorizationModelEdge> getEdgesFrom(AuthorizationModelNode node) {
        return Collections.unmodifiableList(outEdges.getOrDefault(node.getId(), Collections.emptyList()));
    }

    /** Returns all edges in the graph (from → to). */
    public List<AuthorizationModelEdge> getAllEdges() {
        List<AuthorizationModelEdge> all = new ArrayList<>();
        for (List<AuthorizationModelEdge> edgeList : outEdges.values()) {
            all.addAll(edgeList);
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * Returns {@code true} if a path exists from {@code fromLabel} to {@code toLabel}.
     *
     * <p>If both labels are equal, returns {@code true}. Note that intersection
     * and exclusion edges are not specially handled — any edge is traversable.
     *
     * @throws GraphException if either node does not exist
     */
    public boolean pathExists(String fromLabel, String toLabel) throws GraphException {
        AuthorizationModelNode fromNode = getNodeByLabel(fromLabel);
        AuthorizationModelNode toNode = getNodeByLabel(toLabel);

        if (fromNode.getId() == toNode.getId()) {
            return true;
        }

        // BFS
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(fromNode.getId());
        visited.add(fromNode.getId());

        while (!queue.isEmpty()) {
            long current = queue.poll();
            for (AuthorizationModelEdge edge : outEdges.getOrDefault(current, Collections.emptyList())) {
                long nextId = edge.getTo().getId();
                if (nextId == toNode.getId()) {
                    return true;
                }
                if (visited.add(nextId)) {
                    queue.add(nextId);
                }
            }
        }
        return false;
    }

    /**
     * Returns a new graph with all edge directions reversed and the drawing direction toggled.
     */
    public AuthorizationModelGraph reversed() {
        Map<String, AuthorizationModelNode> newNodesByLabel = new HashMap<>(nodesByLabel);
        Map<Long, AuthorizationModelNode> newNodesById = new HashMap<>(nodesById);
        Map<Long, List<AuthorizationModelEdge>> newOutEdges = new HashMap<>();
        Map<Long, List<AuthorizationModelEdge>> newInEdges = new HashMap<>();

        long edgeId = 0;
        for (List<AuthorizationModelEdge> edgeList : outEdges.values()) {
            for (AuthorizationModelEdge edge : edgeList) {
                // flip from and to
                AuthorizationModelEdge reversed = new AuthorizationModelEdge(
                        edgeId++,
                        edge.getTo(),
                        edge.getFrom(),
                        edge.getEdgeType(),
                        edge.getTuplesetRelation(),
                        edge.getConditions());
                newOutEdges
                        .computeIfAbsent(reversed.getFrom().getId(), k -> new ArrayList<>())
                        .add(reversed);
                newInEdges
                        .computeIfAbsent(reversed.getTo().getId(), k -> new ArrayList<>())
                        .add(reversed);
            }
        }

        DrawingDirection newDirection = (drawingDirection == DrawingDirection.LIST_OBJECTS)
                ? DrawingDirection.CHECK
                : DrawingDirection.LIST_OBJECTS;

        return new AuthorizationModelGraph(newNodesByLabel, newNodesById, newOutEdges, newInEdges, newDirection);
    }

    /**
     * Detects cycles in the graph.
     *
     * @return a {@link CycleInformation} describing whether cycles exist.
     */
    public CycleInformation getCycles() {
        List<List<AuthorizationModelNode>> cycles = findAllCycles();
        boolean hasCyclesAtCompileTime = false;
        boolean canHaveCyclesAtRuntime = false;

        for (List<AuthorizationModelNode> cycle : cycles) {
            if (cycleHasNonComputedEdge(cycle)) {
                canHaveCyclesAtRuntime = true;
            } else {
                hasCyclesAtCompileTime = true;
            }
        }

        return new CycleInformation(hasCyclesAtCompileTime, canHaveCyclesAtRuntime);
    }

    /**
     * Returns the DOT representation. The output is stable (sorted).
     * Useful for debugging or visualization (e.g. via Graphviz).
     */
    public String getDOT() {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph {\ngraph [\nrankdir=");
        sb.append(drawingDirection == DrawingDirection.CHECK ? "TB" : "BT");
        sb.append("\n];\n\n// Node definitions.\n");

        // Sort nodes by id for stable output.
        List<AuthorizationModelNode> sortedNodes = new ArrayList<>(nodesById.values());
        sortedNodes.sort(Comparator.comparingLong(AuthorizationModelNode::getId));

        for (AuthorizationModelNode node : sortedNodes) {
            sb.append(node.getId());
            sb.append(" [label=");
            String label = node.getLabel();
            if (needsQuoting(label)) {
                sb.append('"').append(label).append('"');
            } else {
                sb.append(label);
            }
            sb.append("];\n");
        }

        sb.append("\n// Edge definitions.\n");

        // Collect and sort edges for stable output (by from id, then to id, then edge id).
        List<AuthorizationModelEdge> sortedEdges = new ArrayList<>(getAllEdges());
        sortedEdges.sort(Comparator.comparingLong(
                        (AuthorizationModelEdge e) -> e.getFrom().getId())
                .thenComparingLong(e -> e.getTo().getId())
                .thenComparingLong(AuthorizationModelEdge::getId));

        for (AuthorizationModelEdge edge : sortedEdges) {
            sb.append(edge.getFrom().getId());
            sb.append(" -> ");
            sb.append(edge.getTo().getId());

            String attrs = edgeAttributes(edge);
            if (!attrs.isEmpty()) {
                sb.append(" [").append(attrs).append(']');
            }
            sb.append(";\n");
        }

        sb.append('}');
        return sb.toString();
    }

    // --- cycle detection (Johnson's-style via Tarjan SCCs) ---

    /**
     * Find all elementary cycles in the directed graph.
     *
     * <p>Uses Tarjan's algorithm to find SCCs, then uses DFS within each SCC
     * to enumerate elementary cycles.
     */
    private List<List<AuthorizationModelNode>> findAllCycles() {
        // Build compact adjacency from node-ids
        Map<Long, Set<Long>> adj = new HashMap<>();
        for (AuthorizationModelNode n : nodesById.values()) {
            adj.put(n.getId(), new HashSet<>());
        }
        for (List<AuthorizationModelEdge> edgeList : outEdges.values()) {
            for (AuthorizationModelEdge e : edgeList) {
                adj.get(e.getFrom().getId()).add(e.getTo().getId());
            }
        }

        // Tarjan SCC
        List<List<Long>> sccs = tarjanSCC(adj);
        List<List<AuthorizationModelNode>> allCycles = new ArrayList<>();

        for (List<Long> scc : sccs) {
            if (scc.size() == 1) {
                // Self-loop check
                long id = scc.get(0);
                if (adj.getOrDefault(id, Collections.emptySet()).contains(id)) {
                    allCycles.add(Collections.singletonList(nodesById.get(id)));
                }
                continue;
            }
            // Enumerate cycles within the SCC via DFS
            Set<Long> sccSet = new HashSet<>(scc);
            for (long start : scc) {
                findCyclesFrom(start, sccSet, adj, allCycles);
                sccSet.remove(start);
            }
        }

        return allCycles;
    }

    private void findCyclesFrom(
            long start, Set<Long> sccSet, Map<Long, Set<Long>> adj, List<List<AuthorizationModelNode>> result) {
        Deque<Long> stack = new ArrayDeque<>();
        Set<Long> blocked = new HashSet<>();
        Map<Long, Set<Long>> blockedMap = new HashMap<>();
        stack.push(start);
        blocked.add(start);

        // DFS with backtracking
        List<Long> path = new ArrayList<>();
        path.add(start);
        dfsEnumerate(start, start, sccSet, adj, blocked, blockedMap, path, result);
    }

    private boolean dfsEnumerate(
            long current,
            long start,
            Set<Long> sccSet,
            Map<Long, Set<Long>> adj,
            Set<Long> blocked,
            Map<Long, Set<Long>> blockedMap,
            List<Long> path,
            List<List<AuthorizationModelNode>> result) {
        boolean foundCycle = false;

        for (long next : adj.getOrDefault(current, Collections.emptySet())) {
            if (!sccSet.contains(next)) continue;

            if (next == start) {
                // Found a cycle
                List<AuthorizationModelNode> cycle = new ArrayList<>();
                for (long id : path) {
                    cycle.add(nodesById.get(id));
                }
                cycle.add(nodesById.get(start)); // close the loop
                result.add(cycle);
                foundCycle = true;
            } else if (!blocked.contains(next)) {
                blocked.add(next);
                path.add(next);
                if (dfsEnumerate(next, start, sccSet, adj, blocked, blockedMap, path, result)) {
                    foundCycle = true;
                }
                path.remove(path.size() - 1);
            }
        }

        if (foundCycle) {
            unblock(current, blocked, blockedMap);
        } else {
            for (long next : adj.getOrDefault(current, Collections.emptySet())) {
                if (!sccSet.contains(next)) continue;
                blockedMap.computeIfAbsent(next, k -> new HashSet<>()).add(current);
            }
        }

        return foundCycle;
    }

    private void unblock(long node, Set<Long> blocked, Map<Long, Set<Long>> blockedMap) {
        blocked.remove(node);
        Set<Long> dependents = blockedMap.remove(node);
        if (dependents != null) {
            for (long dep : dependents) {
                if (blocked.contains(dep)) {
                    unblock(dep, blocked, blockedMap);
                }
            }
        }
    }

    private List<List<Long>> tarjanSCC(Map<Long, Set<Long>> adj) {
        List<List<Long>> sccs = new ArrayList<>();
        Map<Long, Integer> index = new HashMap<>();
        Map<Long, Integer> lowlink = new HashMap<>();
        Set<Long> onStack = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        int[] counter = {0};

        for (long nodeId : adj.keySet()) {
            if (!index.containsKey(nodeId)) {
                tarjanDFS(nodeId, adj, index, lowlink, onStack, stack, counter, sccs);
            }
        }
        return sccs;
    }

    private void tarjanDFS(
            long v,
            Map<Long, Set<Long>> adj,
            Map<Long, Integer> index,
            Map<Long, Integer> lowlink,
            Set<Long> onStack,
            Deque<Long> stack,
            int[] counter,
            List<List<Long>> sccs) {
        index.put(v, counter[0]);
        lowlink.put(v, counter[0]);
        counter[0]++;
        stack.push(v);
        onStack.add(v);

        for (long w : adj.getOrDefault(v, Collections.emptySet())) {
            if (!index.containsKey(w)) {
                tarjanDFS(w, adj, index, lowlink, onStack, stack, counter, sccs);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.contains(w)) {
                lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
            }
        }

        if (lowlink.get(v).equals(index.get(v))) {
            List<Long> scc = new ArrayList<>();
            long w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (w != v);
            sccs.add(scc);
        }
    }

    /**
     * Returns true if ANY pair of nodes in the cycle list is connected
     * by a non-computed edge. For self-loops (single-node cycles), checks
     * the self-referencing edge.
     */
    private boolean cycleHasNonComputedEdge(List<AuthorizationModelNode> nodeList) {
        // Handle self-loop: single-node cycle
        if (nodeList.size() == 1) {
            long id = nodeList.get(0).getId();
            for (AuthorizationModelEdge edge : outEdges.getOrDefault(id, Collections.emptyList())) {
                if (edge.getTo().getId() == id && edge.getEdgeType() != EdgeType.COMPUTED) {
                    return true;
                }
            }
            return false;
        }

        for (int i = 0; i < nodeList.size(); i++) {
            for (int j = i + 1; j < nodeList.size(); j++) {
                long fromId = nodeList.get(i).getId();
                long toId = nodeList.get(j).getId();
                for (AuthorizationModelEdge edge : outEdges.getOrDefault(fromId, Collections.emptyList())) {
                    if (edge.getTo().getId() == toId && edge.getEdgeType() != EdgeType.COMPUTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // --- DOT helpers ---

    private static boolean needsQuoting(String label) {
        // Quote labels that contain special characters.
        return label.contains("#") || label.contains(":") || label.contains(" ");
    }

    private static String edgeAttributes(AuthorizationModelEdge edge) {
        switch (edge.getEdgeType()) {
            case DIRECT:
                return "label=direct";
            case COMPUTED:
                return "style=dashed";
            case TTU: {
                String headLabel = edge.getTuplesetRelation();
                if (headLabel == null || headLabel.isEmpty()) {
                    headLabel = "missing";
                }
                return "headlabel=\"(" + headLabel + ")\"";
            }
            case REWRITE:
            default:
                return "";
        }
    }
}
