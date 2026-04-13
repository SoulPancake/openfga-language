// ModelUtilsTest.java
package dev.openfga.language.utils;

import static org.junit.jupiter.api.Assertions.*;

import dev.openfga.sdk.api.model.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ModelUtilsTest {
    private TypeDefinition getTypeDefWithModules() {
        return new TypeDefinition()
                .type("type1")
                .relations(Map.of(
                        "relation1", new Userset(),
                        "relation2", new Userset(),
                        "relation3", new Userset(),
                        "relation4", new Userset()))
                .metadata(new Metadata()
                        .module("type_module1")
                        .relations(Map.of(
                                "relation1",
                                new RelationMetadata().module("module1"),
                                "relation2",
                                new RelationMetadata().module(""),
                                "relation3",
                                new RelationMetadata(),
                                "relation5",
                                new RelationMetadata())));
    }

    private TypeDefinition getTypeDefWithoutModules() {
        return new TypeDefinition().type("type2").relations(Map.of("relation7", new Userset()));
    }

    @Test
    public void testGetModuleForObjectTypeRelation_RelationExistsAndHasModule() throws Exception {
        String result = ModelUtils.getModuleForObjectTypeRelation(getTypeDefWithModules(), "relation1");
        assertEquals("module1", result);
    }

    @Test
    public void testGetModuleForObjectTypeRelation_RelationExistsButHasEmptyModule_TypeHasModule() throws Exception {
        String result = ModelUtils.getModuleForObjectTypeRelation(getTypeDefWithModules(), "relation2");
        assertEquals("type_module1", result);
    }

    @Test
    public void testGetModuleForObjectTypeRelation_RelationExistsButNoModule_TypeHasModule() throws Exception {
        String result = ModelUtils.getModuleForObjectTypeRelation(getTypeDefWithModules(), "relation3");
        assertEquals("type_module1", result);
    }

    @Test
    public void testGetModuleForObjectTypeRelation_RelationExistsButNoMetadata_TypeHasModule() throws Exception {
        String result = ModelUtils.getModuleForObjectTypeRelation(getTypeDefWithModules(), "relation4");
        assertEquals("type_module1", result);
    }

    @Test
    public void testGetModuleForObjectTypeRelation_RelationDoesNotExist() {
        Exception exception = assertThrows(Exception.class, () -> {
            ModelUtils.getModuleForObjectTypeRelation(getTypeDefWithModules(), "relation5");
        });

        assertEquals("relation relation5 does not exist in type type1", exception.getMessage());
    }

    @Test
    public void testGetModuleForObjectTypeRelation_RelationDoesNotExist2() {
        Exception exception = assertThrows(Exception.class, () -> {
            ModelUtils.getModuleForObjectTypeRelation(getTypeDefWithModules(), "relation6");
        });

        assertEquals("relation relation6 does not exist in type type1", exception.getMessage());
    }

    @Test
    public void testGetModuleForObjectTypeRelation_RelationExistsButNoModule_TypeNoModule() throws Exception {
        String result = ModelUtils.getModuleForObjectTypeRelation(getTypeDefWithoutModules(), "relation7");
        assertEquals(null, result);
    }

    @Test
    public void testIsRelationAssignable_RelationHasThis() {
        Userset relDef = new Userset();
        relDef.setThis(new Object());

        boolean result = ModelUtils.isRelationAssignable(relDef);
        assertTrue(result);
    }

    @Test
    public void testIsRelationAssignable_RelationHasUnionWithThis() {
        Userset relDef = new Userset();
        Usersets union = new Usersets();
        Userset innerRelDef = new Userset();
        innerRelDef.setThis(new Object());
        union.setChild(List.of(innerRelDef));
        relDef.setUnion(union);

        boolean result = ModelUtils.isRelationAssignable(relDef);
        assertTrue(result);
    }

    @Test
    public void testIsRelationAssignable_RelationHasIntersectionWithThis() {
        Userset relDef = new Userset();
        Usersets intersection = new Usersets();
        Userset innerRelDef = new Userset();
        innerRelDef.setThis(new Object());
        intersection.setChild(List.of(innerRelDef));
        relDef.setIntersection(intersection);

        boolean result = ModelUtils.isRelationAssignable(relDef);
        assertTrue(result);
    }

    @Test
    public void testIsRelationAssignable_RelationHasDifferenceWithBaseThis() {
        Userset relDef = new Userset();
        Difference difference = new Difference();
        Userset innerRelDef = new Userset();
        innerRelDef.setThis(new Object());
        difference.setBase(innerRelDef);
        relDef.setDifference(difference);

        boolean result = ModelUtils.isRelationAssignable(relDef);
        assertTrue(result);
    }

    @Test
    public void testIsRelationAssignable_RelationHasDifferenceWithSubtractThis() {
        Userset relDef = new Userset();
        Difference difference = new Difference();
        Userset innerRelDef = new Userset();
        innerRelDef.setThis(new Object());
        difference.setSubtract(innerRelDef);
        relDef.setDifference(difference);

        boolean result = ModelUtils.isRelationAssignable(relDef);
        assertTrue(result);
    }

    @Test
    public void testIsRelationAssignable_RelationHasNoAssignableKeys() {
        Userset relDef = new Userset();
        Usersets union = new Usersets();
        Usersets intersection = new Usersets();
        intersection.setChild(List.of(new Userset()));
        Userset intersectionUserset = new Userset();
        intersectionUserset.setIntersection(intersection);
        union.setChild(List.of(intersectionUserset));
        relDef.setUnion(union);

        boolean result = ModelUtils.isRelationAssignable(relDef);
        assertFalse(result);
    }

    // isModelModular tests

    @Test
    public void testIsModelModular_Schema12WithTypeModule() {
        var model = new AuthorizationModel()
                .schemaVersion("1.2")
                .typeDefinitions(List.of(new TypeDefinition()
                        .type("user")
                        .relations(Map.of("viewer", new Userset()))
                        .metadata(new Metadata().module("user_module"))));
        assertTrue(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_Schema12WithRelationModule() {
        var model = new AuthorizationModel()
                .schemaVersion("1.2")
                .typeDefinitions(List.of(new TypeDefinition()
                        .type("document")
                        .relations(Map.of("viewer", new Userset()))
                        .metadata(new Metadata()
                                .relations(Map.of("viewer", new RelationMetadata().module("viewer_module"))))));
        assertTrue(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_Schema11NotModular() {
        var model = new AuthorizationModel()
                .schemaVersion("1.1")
                .typeDefinitions(List.of(new TypeDefinition()
                        .type("user")
                        .relations(Map.of("viewer", new Userset()))
                        .metadata(new Metadata().module("user_module"))));
        assertFalse(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_Schema12NoModules() {
        var model = new AuthorizationModel()
                .schemaVersion("1.2")
                .typeDefinitions(List.of(new TypeDefinition().type("user").relations(Map.of("viewer", new Userset()))));
        assertFalse(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_Schema12EmptyModuleString() {
        var model = new AuthorizationModel()
                .schemaVersion("1.2")
                .typeDefinitions(List.of(new TypeDefinition()
                        .type("user")
                        .relations(Map.of("viewer", new Userset()))
                        .metadata(new Metadata().module(""))));
        assertFalse(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_Schema12NoTypeModuleButRelationWithModule() {
        var model = new AuthorizationModel()
                .schemaVersion("1.2")
                .typeDefinitions(List.of(new TypeDefinition()
                        .type("document")
                        .relations(Map.of("viewer", new Userset(), "editor", new Userset()))
                        .metadata(new Metadata()
                                .relations(Map.of("editor", new RelationMetadata().module("editor_module"))))));
        assertTrue(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_NullSchemaVersionThrows() {
        var model = new AuthorizationModel()
                .schemaVersion(null)
                .typeDefinitions(
                        List.of(new TypeDefinition().type("user").metadata(new Metadata().module("user_module"))));
        assertThrows(IllegalArgumentException.class, () -> ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_UnsupportedSchemaVersionThrows() {
        var model = new AuthorizationModel()
                .schemaVersion("2.0")
                .typeDefinitions(
                        List.of(new TypeDefinition().type("user").metadata(new Metadata().module("user_module"))));
        var exception = assertThrows(IllegalArgumentException.class, () -> ModelUtils.isModelModular(model));
        assertEquals("Unsupported schema version: 2.0", exception.getMessage());
    }

    @Test
    public void testIsModelModular_EmptyTypeDefinitions() {
        var model = new AuthorizationModel().schemaVersion("1.2").typeDefinitions(List.of());
        assertFalse(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_NullTypeDefinitions() {
        var model = new AuthorizationModel().schemaVersion("1.2").typeDefinitions(null);
        assertFalse(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_TypeDefWithNullMetadata() {
        var model = new AuthorizationModel()
                .schemaVersion("1.2")
                .typeDefinitions(List.of(new TypeDefinition().type("user")));
        assertFalse(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_TypeDefWithMetadataButNullRelationsMap() {
        var model = new AuthorizationModel()
                .schemaVersion("1.2")
                .typeDefinitions(List.of(new TypeDefinition().type("user").metadata(new Metadata())));
        assertFalse(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_RelationMetadataWithEmptyModuleString() {
        var model = new AuthorizationModel()
                .schemaVersion("1.2")
                .typeDefinitions(List.of(new TypeDefinition()
                        .type("document")
                        .relations(Map.of("viewer", new Userset()))
                        .metadata(new Metadata().relations(Map.of("viewer", new RelationMetadata().module(""))))));
        assertFalse(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsModelModular_RelationMetadataWithNullModule() {
        var model = new AuthorizationModel()
                .schemaVersion("1.2")
                .typeDefinitions(List.of(new TypeDefinition()
                        .type("document")
                        .relations(Map.of("viewer", new Userset()))
                        .metadata(new Metadata().relations(Map.of("viewer", new RelationMetadata())))));
        assertFalse(ModelUtils.isModelModular(model));
    }

    @Test
    public void testIsRelationAssignable_NullRelDef() {
        assertFalse(ModelUtils.isRelationAssignable(null));
    }

    @Test
    public void testIsRelationAssignable_EmptyUserset() {
        assertFalse(ModelUtils.isRelationAssignable(new Userset()));
    }

    @Test
    public void testGetModuleForObjectTypeRelation_NullRelations() {
        TypeDefinition typeDef = new TypeDefinition().type("empty");
        Exception exception =
                assertThrows(Exception.class, () -> ModelUtils.getModuleForObjectTypeRelation(typeDef, "any"));
        assertEquals("relation any does not exist in type empty", exception.getMessage());
    }
}
