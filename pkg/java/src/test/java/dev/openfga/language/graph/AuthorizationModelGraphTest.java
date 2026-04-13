package dev.openfga.language.graph;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import dev.openfga.language.DslToJsonTransformer;
import dev.openfga.sdk.api.model.AuthorizationModel;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuthorizationModelGraphTest {

    private static AuthorizationModel parseModel(String dsl) {
        var result = new DslToJsonTransformer().parseDsl(dsl);
        assertThat(result.IsFailure()).isFalse();
        return result.getAuthorizationModel();
    }

    // ---- helpers ----

    private static AuthorizationModelGraph buildGraph(String dsl) {
        return AuthorizationModelGraphBuilder.build(parseModel(dsl));
    }

    // ======================= Graph Building Tests =======================

    @Nested
    class GraphBuilding {
        @Test
        void directAssignment() {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define viewer: [user]");

            assertThat(graph.getNodes()).hasSize(3); // user, folder, folder#viewer
            assertThat(graph.getAllEdges()).hasSize(1);
            var edge = graph.getAllEdges().get(0);
            assertThat(edge.getEdgeType()).isEqualTo(EdgeType.DIRECT);
            assertThat(edge.getFrom().getLabel()).isEqualTo("user");
            assertThat(edge.getTo().getLabel()).isEqualTo("folder#viewer");
        }

        @Test
        void directAssignmentWithWildcard() {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define viewer: [user:*]");

            assertThatCode(() -> graph.getNodeByLabel("user:*")).doesNotThrowAnyException();
            var wildcardNode = assertDoesNotThrow(() -> graph.getNodeByLabel("user:*"));
            assertThat(wildcardNode.getNodeType()).isEqualTo(NodeType.SPECIFIC_TYPE_WILDCARD);
        }

        @Test
        void computedUserset() {
            var graph = buildGraph(
                    "model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define writer: [user]\n    define viewer: writer");

            var edges = graph.getAllEdges();
            var computedEdges = edges.stream()
                    .filter(e -> e.getEdgeType() == EdgeType.COMPUTED)
                    .collect(Collectors.toList());
            assertThat(computedEdges).hasSize(1);
            assertThat(computedEdges.get(0).getFrom().getLabel()).isEqualTo("folder#writer");
            assertThat(computedEdges.get(0).getTo().getLabel()).isEqualTo("folder#viewer");
        }

        @Test
        void tupleToUserset() {
            var graph = buildGraph(
                    "model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define parent: [folder]\n    define viewer: viewer from parent");

            var ttuEdges = graph.getAllEdges().stream()
                    .filter(e -> e.getEdgeType() == EdgeType.TTU)
                    .collect(Collectors.toList());
            assertThat(ttuEdges).hasSize(1);
            assertThat(ttuEdges.get(0).getTuplesetRelation()).isEqualTo("folder#parent");
        }

        @Test
        void unionOperator() {
            var graph = buildGraph(
                    "model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define writer: [user]\n    define viewer: [user] or writer");

            var operatorNodes = graph.getNodes().stream()
                    .filter(n -> n.getNodeType() == NodeType.OPERATOR)
                    .collect(Collectors.toList());
            assertThat(operatorNodes).hasSize(1);
            assertThat(operatorNodes.get(0).getLabel()).isEqualTo(Operators.UNION);
        }

        @Test
        void intersectionOperator() {
            var graph = buildGraph(
                    "model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define writer: [user]\n    define viewer: [user] and writer");

            var operatorNodes = graph.getNodes().stream()
                    .filter(n -> n.getNodeType() == NodeType.OPERATOR)
                    .collect(Collectors.toList());
            assertThat(operatorNodes).hasSize(1);
            assertThat(operatorNodes.get(0).getLabel()).isEqualTo(Operators.INTERSECTION);
        }

        @Test
        void exclusionOperator() {
            var graph = buildGraph(
                    "model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define writer: [user]\n    define viewer: [user] but not writer");

            var operatorNodes = graph.getNodes().stream()
                    .filter(n -> n.getNodeType() == NodeType.OPERATOR)
                    .collect(Collectors.toList());
            assertThat(operatorNodes).hasSize(1);
            assertThat(operatorNodes.get(0).getLabel()).isEqualTo(Operators.EXCLUSION);
        }

        @Test
        void usersetRelation() {
            var graph = buildGraph(
                    "model\n  schema 1.1\ntype user\ntype group\n  relations\n    define member: [user]\ntype folder\n  relations\n    define viewer: [group#member]");

            var node = assertDoesNotThrow(() -> graph.getNodeByLabel("group#member"));
            assertThat(node.getNodeType()).isEqualTo(NodeType.SPECIFIC_TYPE_AND_RELATION);
        }

        @Test
        void conditionedEdgesAreDeduped() {
            var graph = buildGraph(
                    "model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define viewer: [user, user with cond1]");

            var edges = graph.getAllEdges();
            // Should have one edge with two conditions (empty and cond1)
            var directEdges = edges.stream()
                    .filter(e -> e.getEdgeType() == EdgeType.DIRECT)
                    .collect(Collectors.toList());
            assertThat(directEdges).hasSize(1);
            assertThat(directEdges.get(0).getConditions()).hasSize(2);
        }

        @Test
        void nullTypeDefinitionsHandled() {
            var model = new AuthorizationModel().schemaVersion("1.1").typeDefinitions(null);
            var graph = AuthorizationModelGraphBuilder.build(model);
            assertThat(graph.getNodes()).isEmpty();
            assertThat(graph.getAllEdges()).isEmpty();
        }

        @Test
        void typeWithNoRelations() {
            var graph = buildGraph("model\n  schema 1.1\ntype user");

            assertThat(graph.getNodes()).hasSize(1);
            var node = assertDoesNotThrow(() -> graph.getNodeByLabel("user"));
            assertThat(node.getNodeType()).isEqualTo(NodeType.SPECIFIC_TYPE);
        }

        @Test
        void complexModelWithMultipleTypes() {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype company\n  relations\n"
                    + "    define member: [user]\n    define owner: [user]\n"
                    + "    define approved_member: member or owner\ntype group\n  relations\n"
                    + "    define approved_member: [user]\ntype license\n  relations\n"
                    + "    define active_member: approved_member from owner\n"
                    + "    define owner: [company, group]");

            // Verify key nodes exist
            assertThatCode(() -> graph.getNodeByLabel("user")).doesNotThrowAnyException();
            assertThatCode(() -> graph.getNodeByLabel("company")).doesNotThrowAnyException();
            assertThatCode(() -> graph.getNodeByLabel("group")).doesNotThrowAnyException();
            assertThatCode(() -> graph.getNodeByLabel("license")).doesNotThrowAnyException();
            assertThatCode(() -> graph.getNodeByLabel("company#member")).doesNotThrowAnyException();
            assertThatCode(() -> graph.getNodeByLabel("license#active_member")).doesNotThrowAnyException();
        }

        @Test
        void ttuSkipsNonexistentTypeRelation() {
            // If the tupleset type doesn't have the computed relation, it should be skipped
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype group\n  relations\n    define member: [user]\n"
                            + "type team\ntype folder\n  relations\n    define parent: [group, team]\n"
                            + "    define viewer: member from parent");

            // team doesn't have "member" relation, so only group#member → folder#viewer TTU edge should exist
            var ttuEdges = graph.getAllEdges().stream()
                    .filter(e -> e.getEdgeType() == EdgeType.TTU)
                    .collect(Collectors.toList());
            assertThat(ttuEdges).hasSize(1);
            assertThat(ttuEdges.get(0).getFrom().getLabel()).isEqualTo("group#member");
        }

        @Test
        void ttuDedupesEdgesWithConditions() {
            // When the same TTU relation is referenced via conditioned types,
            // the edge should be deduped (e.g. parent: [group, group with condX])
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype group\n  relations\n"
                    + "    define member: [user]\ntype folder\n  relations\n"
                    + "    define parent: [group, group with condX]\n"
                    + "    define viewer: member from parent");

            var ttuEdges = graph.getAllEdges().stream()
                    .filter(e -> e.getEdgeType() == EdgeType.TTU)
                    .collect(Collectors.toList());
            // Should only have one TTU edge (deduped), not two
            assertThat(ttuEdges).hasSize(1);
        }

        @Test
        void multipleParentsTTU() {
            // Both group and team have the relation, so both TTU edges should exist
            var graph = buildGraph("model\n  schema 1.1\ntype user\n"
                    + "type group\n  relations\n    define viewer: [user]\n"
                    + "type team\n  relations\n    define viewer: [user]\n"
                    + "type folder\n  relations\n    define parent: [group, team]\n"
                    + "    define can_view: viewer from parent");

            var ttuEdges = graph.getAllEdges().stream()
                    .filter(e -> e.getEdgeType() == EdgeType.TTU)
                    .collect(Collectors.toList());
            assertThat(ttuEdges).hasSize(2);
        }
    }

    // ======================= GetNodeByLabel Tests =======================

    @Nested
    class GetNodeByLabel {
        @Test
        void findsExistingNodes() throws GraphException {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype company\n  relations\n"
                    + "    define member: [user with cond, user:* with cond]\n"
                    + "    define owner: [user]\n    define approved_member: member or owner\n"
                    + "type group\n  relations\n    define approved_member: [user]\n"
                    + "type license\n  relations\n    define active_member: approved_member from owner\n"
                    + "    define owner: [company, group]");

            assertThat(graph.getNodeByLabel("user")).isNotNull();
            assertThat(graph.getNodeByLabel("user:*")).isNotNull();
            assertThat(graph.getNodeByLabel("company")).isNotNull();
            assertThat(graph.getNodeByLabel("company#member")).isNotNull();
            assertThat(graph.getNodeByLabel("company#owner")).isNotNull();
            assertThat(graph.getNodeByLabel("company#approved_member")).isNotNull();
            assertThat(graph.getNodeByLabel("group")).isNotNull();
            assertThat(graph.getNodeByLabel("group#approved_member")).isNotNull();
            assertThat(graph.getNodeByLabel("license")).isNotNull();
            assertThat(graph.getNodeByLabel("license#active_member")).isNotNull();
            assertThat(graph.getNodeByLabel("license#owner")).isNotNull();
        }

        @Test
        void throwsForNonExistentNodes() {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype company\n  relations\n    define member: [user]");

            assertThatThrownBy(() -> graph.getNodeByLabel("unknown")).isInstanceOf(GraphException.class);
            assertThatThrownBy(() -> graph.getNodeByLabel("unknown#unknown")).isInstanceOf(GraphException.class);
        }
    }

    // ======================= NodeType Tests =======================

    @Nested
    class NodeTypes {
        @Test
        void returnsCorrectNodeTypes() throws GraphException {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype group\n  relations\n    define member: [user]\n"
                            + "type company\n  relations\n    define wildcard: [user:*]\n"
                            + "    define direct: [user]\n    define userset: [group#member]\n"
                            + "    define intersectionRelation: wildcard and direct\n"
                            + "    define unionRelation: wildcard or direct\n"
                            + "    define differenceRelation: wildcard but not direct");

            assertThat(graph.getNodeByLabel("user").getNodeType()).isEqualTo(NodeType.SPECIFIC_TYPE);
            assertThat(graph.getNodeByLabel("user:*").getNodeType()).isEqualTo(NodeType.SPECIFIC_TYPE_WILDCARD);
            assertThat(graph.getNodeByLabel("group").getNodeType()).isEqualTo(NodeType.SPECIFIC_TYPE);
            assertThat(graph.getNodeByLabel("group#member").getNodeType())
                    .isEqualTo(NodeType.SPECIFIC_TYPE_AND_RELATION);
            assertThat(graph.getNodeByLabel("company").getNodeType()).isEqualTo(NodeType.SPECIFIC_TYPE);
            assertThat(graph.getNodeByLabel("company#wildcard").getNodeType())
                    .isEqualTo(NodeType.SPECIFIC_TYPE_AND_RELATION);
            assertThat(graph.getNodeByLabel("company#direct").getNodeType())
                    .isEqualTo(NodeType.SPECIFIC_TYPE_AND_RELATION);
            assertThat(graph.getNodeByLabel("company#userset").getNodeType())
                    .isEqualTo(NodeType.SPECIFIC_TYPE_AND_RELATION);

            // Verify operator nodes exist
            var operatorNodes = graph.getNodes().stream()
                    .filter(n -> n.getNodeType() == NodeType.OPERATOR)
                    .collect(Collectors.toList());
            var labels =
                    operatorNodes.stream().map(AuthorizationModelNode::getLabel).collect(Collectors.toSet());
            assertThat(labels).containsExactlyInAnyOrder(Operators.UNION, Operators.INTERSECTION, Operators.EXCLUSION);
        }
    }

    // ======================= PathExists Tests =======================

    // Method sources must be in the top-level class for Java 11 compatibility.
    static Stream<Arguments> usersetComputedUsersetPaths() {
        return Stream.of(
                Arguments.of("user", "group#member", true, false),
                Arguments.of("employee", "group#member", true, false),
                Arguments.of("wild:*", "group#member", true, false),
                Arguments.of("other", "group#member", false, false),
                Arguments.of("group", "group#member", false, false),
                Arguments.of("foo", "group#member", false, true),
                Arguments.of("user", "group#undefined", false, true),
                Arguments.of("group#member", "group#member", true, false),
                Arguments.of("group#member", "folder#viewer", true, false),
                Arguments.of("group#rootMember", "folder#viewer", true, false),
                Arguments.of("user", "folder#viewer", true, false),
                Arguments.of("employee", "folder#viewer", true, false),
                Arguments.of("wild:*", "folder#viewer", true, false),
                Arguments.of("other", "folder#viewer", false, false));
    }

    static Stream<Arguments> nestedComputedUsersetPaths() {
        return Stream.of(
                Arguments.of("user", "group#member", true),
                Arguments.of("employee", "group#member", true),
                Arguments.of("wild:*", "group#member", true),
                Arguments.of("other", "group#member", false),
                Arguments.of("group", "group#member", false),
                Arguments.of("group#member", "group#member", true),
                Arguments.of("group#member", "folder#viewer", true),
                Arguments.of("user", "folder#viewer", true),
                Arguments.of("employee", "folder#viewer", true),
                Arguments.of("wild:*", "folder#viewer", true),
                Arguments.of("other", "folder#viewer", false));
    }

    static Stream<Arguments> unionRelationPaths() {
        return Stream.of(
                Arguments.of("user", "group#member", true),
                Arguments.of("employee", "group#member", true),
                Arguments.of("wild:*", "group#member", true),
                Arguments.of("other", "group#member", false),
                Arguments.of("group", "group#member", false),
                Arguments.of("group#member", "folder#viewer", true),
                Arguments.of("user", "folder#viewer", true),
                Arguments.of("employee", "folder#viewer", true),
                Arguments.of("wild:*", "folder#viewer", true),
                Arguments.of("other", "folder#viewer", false));
    }

    static Stream<Arguments> intersectionRelationPaths() {
        return Stream.of(
                Arguments.of("user", "group#member", true),
                Arguments.of("employee", "group#member", true),
                Arguments.of("wild:*", "group#member", true),
                Arguments.of("other", "group#member", false),
                Arguments.of("group", "group#member", false),
                Arguments.of("user", "folder#viewer", true),
                Arguments.of("other", "folder#viewer", false));
    }

    static Stream<Arguments> exclusionRelationPaths() {
        return Stream.of(
                Arguments.of("user", "group#member", true),
                Arguments.of("employee", "group#member", true),
                Arguments.of("other", "group#member", false),
                Arguments.of("group", "group#member", false),
                Arguments.of("user", "folder#viewer", true),
                Arguments.of("employee", "folder#viewer", true),
                Arguments.of("other", "folder#viewer", false));
    }

    static Stream<Arguments> ttuPaths() {
        return Stream.of(
                Arguments.of("user", "group#member", true),
                Arguments.of("employee", "group#member", true),
                Arguments.of("wild:*", "group#member", true),
                Arguments.of("other", "group#member", false),
                Arguments.of("group", "group#member", false),
                Arguments.of("group#member", "folder#viewer", true),
                Arguments.of("group#rootMember", "folder#viewer", true),
                Arguments.of("user", "folder#viewer", true),
                Arguments.of("employee", "folder#viewer", true),
                Arguments.of("wild:*", "folder#viewer", true),
                Arguments.of("other", "folder#viewer", false));
    }

    @Nested
    class PathExistence {

        @ParameterizedTest
        @MethodSource("dev.openfga.language.graph.AuthorizationModelGraphTest#usersetComputedUsersetPaths")
        void usersetComputedUserset(String from, String to, boolean expectPath, boolean expectError) {
            var graph = buildGraph("model\n  schema 1.1\ntype other\ntype user\ntype wild\ntype employee\n"
                    + "type group\n  relations\n    define rootMember: [user, user:*, employee, wild:*]\n"
                    + "    define member: rootMember\ntype folder\n  relations\n"
                    + "    define viewer: [group#member]");

            if (expectError) {
                assertThatThrownBy(() -> graph.pathExists(from, to)).isInstanceOf(GraphException.class);
            } else {
                boolean actual = assertDoesNotThrow(() -> graph.pathExists(from, to));
                assertThat(actual).isEqualTo(expectPath);
            }
        }

        @ParameterizedTest
        @MethodSource("dev.openfga.language.graph.AuthorizationModelGraphTest#nestedComputedUsersetPaths")
        void nestedComputedUserset(String from, String to, boolean expectPath) {
            var graph = buildGraph("model\n  schema 1.1\ntype other\ntype user\ntype employee\ntype wild\n"
                    + "type group\n  relations\n"
                    + "    define member: [user, user:*, employee, wild:*, group#member]\n"
                    + "type folder\n  relations\n    define viewer: [group#member]");

            boolean actual = assertDoesNotThrow(() -> graph.pathExists(from, to));
            assertThat(actual).isEqualTo(expectPath);
        }

        @ParameterizedTest
        @MethodSource("dev.openfga.language.graph.AuthorizationModelGraphTest#unionRelationPaths")
        void unionRelation(String from, String to, boolean expectPath) {
            var graph = buildGraph("model\n  schema 1.1\ntype other\ntype user\ntype employee\ntype wild\n"
                    + "type group\n  relations\n    define child1: [user, user:*]\n"
                    + "    define child2: [employee]\n    define child3: [wild:*]\n"
                    + "    define member: child1 or child2 or child3\n"
                    + "type folder\n  relations\n    define viewer: [group#member]");

            boolean actual = assertDoesNotThrow(() -> graph.pathExists(from, to));
            assertThat(actual).isEqualTo(expectPath);
        }

        @ParameterizedTest
        @MethodSource("dev.openfga.language.graph.AuthorizationModelGraphTest#intersectionRelationPaths")
        void intersectionRelation(String from, String to, boolean expectPath) {
            var graph = buildGraph("model\n  schema 1.1\ntype other\ntype user\ntype employee\ntype wild\n"
                    + "type group\n  relations\n    define child1: [user]\n"
                    + "    define child2: [user, employee, wild:*]\n"
                    + "    define member: child1 and child2\n"
                    + "type folder\n  relations\n    define viewer: [group#member]");

            boolean actual = assertDoesNotThrow(() -> graph.pathExists(from, to));
            assertThat(actual).isEqualTo(expectPath);
        }

        @ParameterizedTest
        @MethodSource("dev.openfga.language.graph.AuthorizationModelGraphTest#exclusionRelationPaths")
        void exclusionRelation(String from, String to, boolean expectPath) {
            var graph = buildGraph("model\n  schema 1.1\ntype other\ntype user\ntype employee\n"
                    + "type group\n  relations\n    define child1: [user]\n"
                    + "    define child2: [user, employee]\n"
                    + "    define member: child1 but not child2\n"
                    + "type folder\n  relations\n    define viewer: [group#member]");

            boolean actual = assertDoesNotThrow(() -> graph.pathExists(from, to));
            assertThat(actual).isEqualTo(expectPath);
        }

        @ParameterizedTest
        @MethodSource("dev.openfga.language.graph.AuthorizationModelGraphTest#ttuPaths")
        void tupleToUserset(String from, String to, boolean expectPath) {
            var graph = buildGraph("model\n  schema 1.1\ntype other\ntype user\ntype wild\ntype employee\n"
                    + "type group\n  relations\n"
                    + "    define rootMember: [user, user:*, employee, wild:*]\n"
                    + "    define member: rootMember\n"
                    + "type folder\n  relations\n    define parent: [group]\n"
                    + "    define viewer: member from parent");

            boolean actual = assertDoesNotThrow(() -> graph.pathExists(from, to));
            assertThat(actual).isEqualTo(expectPath);
        }

        @Test
        void selfPathAlwaysTrue() {
            var graph = buildGraph("model\n  schema 1.1\ntype user");

            boolean actual = assertDoesNotThrow(() -> graph.pathExists("user", "user"));
            assertThat(actual).isTrue();
        }
    }

    // ======================= Drawing Direction Tests =======================

    @Nested
    class DrawingDirectionTests {
        @Test
        void defaultDirectionIsListObjects() {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype company\n  relations\n    define member: [user]");

            assertThat(graph.getDrawingDirection()).isEqualTo(DrawingDirection.LIST_OBJECTS);
        }

        @Test
        void reversedDirectionIsCheck() {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype company\n  relations\n    define member: [user]");

            var reversed = graph.reversed();
            assertThat(reversed.getDrawingDirection()).isEqualTo(DrawingDirection.CHECK);
        }

        @Test
        void doubleReversalRestoresOriginalDirection() {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype company\n  relations\n    define member: [user]");

            var doubleReversed = graph.reversed().reversed();
            assertThat(doubleReversed.getDrawingDirection()).isEqualTo(DrawingDirection.LIST_OBJECTS);
        }
    }

    // ======================= Reversed Graph Tests =======================

    @Nested
    class ReversedGraphTests {
        @Test
        void reversedGraphFlipsEdges() {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define viewer: [user]");

            var original = graph.getAllEdges();
            assertThat(original).hasSize(1);
            assertThat(original.get(0).getFrom().getLabel()).isEqualTo("user");
            assertThat(original.get(0).getTo().getLabel()).isEqualTo("folder#viewer");

            var reversed = graph.reversed();
            var reversedEdges = reversed.getAllEdges();
            assertThat(reversedEdges).hasSize(1);
            assertThat(reversedEdges.get(0).getFrom().getLabel()).isEqualTo("folder#viewer");
            assertThat(reversedEdges.get(0).getTo().getLabel()).isEqualTo("user");
        }

        @Test
        void reversedGraphPreservesNodeCount() {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype company\n  relations\n"
                    + "    define member: [user]\n    define owner: [user]\n"
                    + "    define approved_member: member or owner");

            var reversed = graph.reversed();
            assertThat(reversed.getNodes()).hasSize(graph.getNodes().size());
        }

        @Test
        void reversedGraphPreservesEdgeCount() {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype company\n  relations\n"
                    + "    define member: [user]\n    define owner: [user]\n"
                    + "    define approved_member: member or owner");

            var reversed = graph.reversed();
            assertThat(reversed.getAllEdges()).hasSize(graph.getAllEdges().size());
        }

        @Test
        void reversedGraphPreservesEdgeTypes() {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n"
                    + "    define parent: [folder]\n    define viewer: viewer from parent");

            var original = graph.getAllEdges();
            var reversed = graph.reversed();
            var reversedEdges = reversed.getAllEdges();

            // Should have same set of edge types
            var originalTypes =
                    original.stream().map(AuthorizationModelEdge::getEdgeType).collect(Collectors.toSet());
            var reversedTypes = reversedEdges.stream()
                    .map(AuthorizationModelEdge::getEdgeType)
                    .collect(Collectors.toSet());
            assertThat(reversedTypes).isEqualTo(originalTypes);
        }

        @Test
        void reversedComplexModelDOT() {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype company\n  relations\n"
                    + "    define member: [user]\n    define owner: [user]\n"
                    + "    define approved_member: member or owner\n"
                    + "type group\n  relations\n    define approved_member: [user]\n"
                    + "type license\n  relations\n"
                    + "    define active_member: approved_member from owner\n"
                    + "    define owner: [company, group]");

            var reversed = graph.reversed();
            var dot = reversed.getDOT();
            // Should be top-to-bottom for Check direction
            assertThat(dot).contains("rankdir=TB");
            // DOT output should be non-empty
            assertThat(dot).isNotEmpty();
        }
    }

    // ======================= DOT Representation Tests =======================

    @Nested
    class DOTRepresentation {
        @Test
        void directAssignment() {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype folder\n  relations\n    define viewer: [user]\ntype user");

            var dot = graph.getDOT();

            // Verify basic structure
            assertThat(dot).contains("digraph {");
            assertThat(dot).contains("rankdir=BT");
            assertThat(dot).contains("label=folder");
            assertThat(dot).contains("label=\"folder#viewer\"");
            assertThat(dot).contains("label=user");
            assertThat(dot).contains("[label=direct]");
        }

        @Test
        void directAssignmentWithWildcard() {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype folder\n  relations\n    define viewer: [user:*]\ntype user");

            var dot = graph.getDOT();
            assertThat(dot).contains("label=\"user:*\"");
        }

        @Test
        void computedEdgeHasDashedStyle() {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n"
                    + "    define writer: [user]\n    define viewer: writer");

            var dot = graph.getDOT();
            assertThat(dot).contains("style=dashed");
        }

        @Test
        void ttuEdgeShowsHeadlabel() {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n"
                    + "    define parent: [folder]\n    define viewer: viewer from parent");

            var dot = graph.getDOT();
            assertThat(dot).contains("headlabel=\"(folder#parent)\"");
        }

        @Test
        void operatorNodesAreLabeled() {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n"
                    + "    define writer: [user]\n    define viewer: [user] or writer");

            var dot = graph.getDOT();
            assertThat(dot).contains("label=union");
        }

        @Test
        void stableNodeOrdering() {
            // Build the same model twice and verify DOT output is consistent
            var dot1 = buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define viewer: [user]")
                    .getDOT();
            var dot2 = buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define viewer: [user]")
                    .getDOT();
            assertThat(dot1).isEqualTo(dot2);
        }
    }

    // ======================= Cycle Detection Tests =======================

    @Nested
    class CycleDetection {
        @Test
        void noCyclesInSimpleModel() {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define viewer: [user]");

            var cycles = graph.getCycles();
            assertThat(cycles.hasCyclesAtCompileTime()).isFalse();
            assertThat(cycles.canHaveCyclesAtRuntime()).isFalse();
        }

        @Test
        void computedCycleDetectedAtCompileTime() {
            // A pure computed userset cycle: a -> b -> a
            var graph = buildGraph(
                    "model\n  schema 1.1\ntype user\ntype folder\n  relations\n" + "    define a: b\n    define b: a");

            var cycles = graph.getCycles();
            assertThat(cycles.hasCyclesAtCompileTime()).isTrue();
        }

        @Test
        void selfReferentialTuplesetCycleDetectedAtRuntime() {
            // A recursive relation via TTU that can cause runtime cycles
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n"
                    + "    define parent: [folder]\n"
                    + "    define viewer: [user] or viewer from parent");

            var cycles = graph.getCycles();
            assertThat(cycles.canHaveCyclesAtRuntime()).isTrue();
        }

        @Test
        void noCyclesWithUnionOfUnrelated() {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype employee\ntype folder\n  relations\n"
                    + "    define writer: [user]\n    define editor: [employee]\n"
                    + "    define viewer: writer or editor");

            var cycles = graph.getCycles();
            assertThat(cycles.hasCyclesAtCompileTime()).isFalse();
            assertThat(cycles.canHaveCyclesAtRuntime()).isFalse();
        }

        @Test
        void directAssignmentSelfReferentialCycleDetectedAtRuntime() {
            // group#member: [group#member] creates a runtime cycle
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype group\n  relations\n"
                    + "    define member: [user, group#member]");

            var cycles = graph.getCycles();
            assertThat(cycles.canHaveCyclesAtRuntime()).isTrue();
        }

        @Test
        void multiNodeCycleDetectedAtRuntime() {
            // folder#viewer: viewer from parent, parent: [folder] creates a runtime cycle through TTU
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n"
                    + "    define parent: [folder]\n"
                    + "    define viewer: [user] or viewer from parent");

            var cycles = graph.getCycles();
            assertThat(cycles.canHaveCyclesAtRuntime()).isTrue();
        }

        @Test
        void multiNodeComputedCyclePlusRuntimeCycle() {
            // Test a model with both types of cycles
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype group\n  relations\n"
                    + "    define a: b\n    define b: a\n"
                    + "    define member: [user, group#member]");

            var cycles = graph.getCycles();
            assertThat(cycles.hasCyclesAtCompileTime()).isTrue();
            assertThat(cycles.canHaveCyclesAtRuntime()).isTrue();
        }
    }

    // ======================= Edge Tests =======================

    @Nested
    class EdgeTests {
        @Test
        void ttuEdgeTuplesetRelation() throws GraphException {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype folder\ntype document\n  relations\n"
                    + "    define viewer: viewer from parent\n"
                    + "    define parent: [document, folder]");

            var viewerNode = graph.getNodeByLabel("document#viewer");
            var edges = graph.getEdgesFrom(viewerNode);

            // There should be TTU edges
            var ttuEdges =
                    edges.stream().filter(e -> e.getEdgeType() == EdgeType.TTU).collect(Collectors.toList());
            assertThat(ttuEdges).isNotEmpty();

            for (var edge : ttuEdges) {
                assertThat(edge.getTuplesetRelation()).isEqualTo("document#parent");
            }
        }

        @Test
        void getEdgesFromReturnsEmptyForLeafNode() {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define viewer: [user]");

            // "folder" has no outgoing edges (folder#viewer does, but folder itself does not)
            var folderNode = assertDoesNotThrow(() -> graph.getNodeByLabel("folder"));
            assertThat(graph.getEdgesFrom(folderNode)).isEmpty();
        }

        @Test
        void edgeConditions() {
            var graph = buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n"
                    + "    define viewer: [user, user with condX]");

            var edges = graph.getAllEdges().stream()
                    .filter(e -> e.getEdgeType() == EdgeType.DIRECT)
                    .collect(Collectors.toList());
            assertThat(edges).hasSize(1);
            // Should have empty condition and condX
            assertThat(edges.get(0).getConditions()).hasSize(2);
            assertThat(edges.get(0).getConditions()).contains("condX");
        }
    }

    // ======================= Node/Edge Equality and toString =======================

    @Nested
    class NodeEdgeMisc {
        @Test
        void nodeEquality() throws GraphException {
            var graph = buildGraph("model\n  schema 1.1\ntype user");

            var node1 = graph.getNodeByLabel("user");
            var node2 = graph.getNodeByLabel("user");
            assertThat(node1).isEqualTo(node2);
            assertThat(node1.hashCode()).isEqualTo(node2.hashCode());
        }

        @Test
        void nodeNotEqualToOtherNode() throws GraphException {
            var graph =
                    buildGraph("model\n  schema 1.1\ntype user\ntype folder\n  relations\n    define viewer: [user]");

            var userNode = graph.getNodeByLabel("user");
            var folderNode = graph.getNodeByLabel("folder");
            assertThat(userNode).isNotEqualTo(folderNode);
        }

        @Test
        void nodeNotEqualToNull() throws GraphException {
            var graph = buildGraph("model\n  schema 1.1\ntype user");

            var node = graph.getNodeByLabel("user");
            assertThat(node).isNotEqualTo(null);
        }

        @Test
        void nodeNotEqualToOtherClass() throws GraphException {
            var graph = buildGraph("model\n  schema 1.1\ntype user");

            var node = graph.getNodeByLabel("user");
            assertThat(node).isNotEqualTo("user");
        }

        @Test
        void nodeToString() throws GraphException {
            var graph = buildGraph("model\n  schema 1.1\ntype user");

            var node = graph.getNodeByLabel("user");
            assertThat(node.toString()).isEqualTo("user");
        }
    }

    // ======================= Enum Values =======================

    @Nested
    class EnumTests {
        @Test
        void nodeTypeValues() {
            assertThat(NodeType.values())
                    .containsExactlyInAnyOrder(
                            NodeType.SPECIFIC_TYPE,
                            NodeType.SPECIFIC_TYPE_AND_RELATION,
                            NodeType.OPERATOR,
                            NodeType.SPECIFIC_TYPE_WILDCARD);
        }

        @Test
        void edgeTypeValues() {
            assertThat(EdgeType.values())
                    .containsExactlyInAnyOrder(EdgeType.DIRECT, EdgeType.REWRITE, EdgeType.TTU, EdgeType.COMPUTED);
        }

        @Test
        void drawingDirectionValues() {
            assertThat(DrawingDirection.values())
                    .containsExactlyInAnyOrder(DrawingDirection.LIST_OBJECTS, DrawingDirection.CHECK);
        }

        @Test
        void operatorConstants() {
            assertThat(Operators.UNION).isEqualTo("union");
            assertThat(Operators.INTERSECTION).isEqualTo("intersection");
            assertThat(Operators.EXCLUSION).isEqualTo("exclusion");
        }
    }

    // ======================= CycleInformation =======================

    @Nested
    class CycleInformationTests {
        @Test
        void cycleInfoBothFalse() {
            var info = new CycleInformation(false, false);
            assertThat(info.hasCyclesAtCompileTime()).isFalse();
            assertThat(info.canHaveCyclesAtRuntime()).isFalse();
        }

        @Test
        void cycleInfoCompileTimeOnly() {
            var info = new CycleInformation(true, false);
            assertThat(info.hasCyclesAtCompileTime()).isTrue();
            assertThat(info.canHaveCyclesAtRuntime()).isFalse();
        }

        @Test
        void cycleInfoRuntimeOnly() {
            var info = new CycleInformation(false, true);
            assertThat(info.hasCyclesAtCompileTime()).isFalse();
            assertThat(info.canHaveCyclesAtRuntime()).isTrue();
        }

        @Test
        void cycleInfoBothTrue() {
            var info = new CycleInformation(true, true);
            assertThat(info.hasCyclesAtCompileTime()).isTrue();
            assertThat(info.canHaveCyclesAtRuntime()).isTrue();
        }
    }

    // ======================= GraphException =======================

    @Nested
    class GraphExceptionTests {
        @Test
        void exceptionMessage() {
            var ex = new GraphException("test message");
            assertThat(ex.getMessage()).isEqualTo("test message");
        }
    }
}
