package dev.openfga.language.utils;

import dev.openfga.sdk.api.model.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ModelUtils {

    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = new HashSet<>(Arrays.asList("1.1", "1.2"));
    private static final Set<String> MODULE_SUPPORTING_SCHEMA_VERSIONS = new HashSet<>(Arrays.asList("1.2"));

    /**
     * getModuleForObjectTypeRelation returns the module for the given object type and relation in that type.
     *
     * @param typeDef  A TypeDefinition object which contains metadata about the type.
     * @param relation A string representing the relation whose module is to be retrieved.
     * @return A string representing the module for the given object type and relation.
     * @throws Exception An error if the relation does not exist.
     */
    public static String getModuleForObjectTypeRelation(TypeDefinition typeDef, String relation) throws Exception {
        Map<String, Userset> relations = typeDef.getRelations();
        if (relations == null || !relations.containsKey(relation)) {
            throw new Exception("relation " + relation + " does not exist in type " + typeDef.getType());
        }

        Metadata metadata = typeDef.getMetadata();
        if (metadata == null || metadata.getRelations() == null) {
            return null;
        }

        RelationMetadata relationMetadata = metadata.getRelations().get(relation);
        if (relationMetadata == null
                || relationMetadata.getModule() == null
                || relationMetadata.getModule().isEmpty()) {
            return metadata.getModule();
        }

        return relationMetadata.getModule();
    }

    /**
     * isRelationAssignable returns true if the relation is assignable, as in the relation definition has a key "this" or
     * any of its children have a key "this".
     *
     * @param relDef A Userset object representing a relation definition.
     * @return A boolean representing whether the relation definition has a key "this".
     */
    public static boolean isRelationAssignable(Userset relDef) {
        if (relDef == null) {
            return false;
        }

        if (relDef.getThis() != null) {
            return true;
        } else if (relDef.getUnion() != null) {
            for (Userset child : relDef.getUnion().getChild()) {
                if (isRelationAssignable(child)) {
                    return true;
                }
            }
        } else if (relDef.getIntersection() != null) {
            for (Userset child : relDef.getIntersection().getChild()) {
                if (isRelationAssignable(child)) {
                    return true;
                }
            }
        } else if (relDef.getDifference() != null) {
            var diff = relDef.getDifference();
            if (isRelationAssignable(diff.getBase()) || isRelationAssignable(diff.getSubtract())) {
                return true;
            }
        }

        // ComputedUserset and TupleToUserset are not assignable
        return false;
    }

    /**
     * isModelModular returns true if the model is modular.
     * A model is modular if it has schema version 1.2 and has at least one relation or object
     * that has a module defined in its metadata.
     *
     * @param model An AuthorizationModel object.
     * @return A boolean representing whether the model is modular.
     * @throws IllegalArgumentException if the model's schema version is not recognized.
     */
    public static boolean isModelModular(AuthorizationModel model) {
        var schemaVersion = model.getSchemaVersion();

        if (schemaVersion == null || !SUPPORTED_SCHEMA_VERSIONS.contains(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported schema version: " + schemaVersion);
        }

        if (!MODULE_SUPPORTING_SCHEMA_VERSIONS.contains(schemaVersion)) {
            return false;
        }

        var typeDefs = model.getTypeDefinitions();
        if (typeDefs == null) {
            return false;
        }

        for (var typeDef : typeDefs) {
            var metadata = typeDef.getMetadata();
            if (metadata == null) {
                continue;
            }

            // Check if the type itself has a module
            if (metadata.getModule() != null && !metadata.getModule().isEmpty()) {
                return true;
            }

            // Check if any relation has a module defined
            var relationsMetadata = metadata.getRelations();
            if (relationsMetadata != null) {
                for (var relationMetadata : relationsMetadata.values()) {
                    if (relationMetadata.getModule() != null
                            && !relationMetadata.getModule().isEmpty()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
