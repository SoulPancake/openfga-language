package dev.openfga.language;

import dev.openfga.language.errors.ModuleTransformationError;
import dev.openfga.language.errors.ModuleTransformationSingleError;
import dev.openfga.language.errors.ParsingError;
import dev.openfga.language.errors.StartEnd;
import dev.openfga.sdk.api.model.*;
import java.util.*;

/**
 * Transforms multiple module files into a singular AuthorizationModel.
 */
public class ModuleTransformer {

    /**
     * Transforms the provided module files into a singular authorization model.
     *
     * @param modules       List of module files to transform.
     * @param schemaVersion The schema version for the resulting model.
     * @return The combined AuthorizationModel.
     * @throws ModuleTransformationError if transformation errors occur.
     */
    public static AuthorizationModel transformModuleFilesToModel(List<ModuleFile> modules, String schemaVersion)
            throws ModuleTransformationError {
        var model = new AuthorizationModel();
        model.setSchemaVersion(schemaVersion);
        model.setTypeDefinitions(new ArrayList<>());
        model.setConditions(new HashMap<>());

        var rawTypeDefs = new ArrayList<TypeDefinition>();
        var types = new HashSet<String>();
        var extendedTypeDefs = new LinkedHashMap<String, List<TypeDefinition>>();
        var conditions = new LinkedHashMap<String, Condition>();
        var moduleFiles = new LinkedHashMap<String, String[]>();

        var transformErrors = new ArrayList<ParsingError>();

        var transformer = new DslToJsonTransformer();

        for (var module : modules) {
            var lines = module.getContents().split("\n", -1);
            moduleFiles.put(module.getName(), lines);

            var result = transformer.parseModularDsl(module.getContents());
            if (result.IsFailure()) {
                for (var error : result.getErrors()) {
                    error.setFile(module.getName());
                    transformErrors.add(error);
                }
                continue;
            }

            var mdl = result.getAuthorizationModel();
            var typeDefExtensions = result.getTypeDefExtensions();

            if (mdl.getTypeDefinitions() != null) {
                for (var typeDef : mdl.getTypeDefinitions()) {
                    boolean isExtension = typeDefExtensions.containsKey(typeDef.getType());

                    if (types.contains(typeDef.getType()) && !isExtension) {
                        var lineIndex = getTypeLineNumber(typeDef.getType(), lines);
                        var lineCol = constructLineAndColumnData(lines, lineIndex, typeDef.getType());

                        transformErrors.add(new ModuleTransformationSingleError(
                                "duplicate type definition " + typeDef.getType(),
                                module.getName(),
                                lineCol[0],
                                lineCol[1]));
                        continue;
                    }

                    if (isExtension) {
                        extendedTypeDefs
                                .computeIfAbsent(module.getName(), k -> new ArrayList<>())
                                .add(typeDef);
                        continue;
                    }

                    if (typeDef.getMetadata() == null) {
                        transformErrors.add(new ModuleTransformationSingleError(
                                "file is not a module", module.getName(), new StartEnd(0, 0), new StartEnd(0, 0)));
                        continue;
                    }

                    typeDef.getMetadata().setSourceInfo(new SourceInfo()._file(module.getName()));
                    types.add(typeDef.getType());
                    rawTypeDefs.add(typeDef);
                }
            }

            if (mdl.getConditions() != null) {
                for (var entry : mdl.getConditions().entrySet()) {
                    var conditionName = entry.getKey();
                    var condition = entry.getValue();

                    if (conditions.containsKey(conditionName)) {
                        var lineIndex = getConditionLineNumber(conditionName, lines);
                        var lineCol = constructLineAndColumnData(lines, lineIndex, conditionName);

                        transformErrors.add(new ModuleTransformationSingleError(
                                "duplicate condition " + conditionName, module.getName(), lineCol[0], lineCol[1]));
                        continue;
                    }

                    if (condition.getMetadata() == null) {
                        condition.setMetadata(new ConditionMetadata());
                    }
                    condition.getMetadata().setSourceInfo(new SourceInfo()._file(module.getName()));
                    conditions.put(conditionName, condition);
                }
            }
        }

        // Process extended type defs
        for (var entry : extendedTypeDefs.entrySet()) {
            var filename = entry.getKey();
            var typeDefs = entry.getValue();
            var lines = moduleFiles.get(filename);

            for (var typeDef : typeDefs) {
                var originalIndex = findTypeDefIndex(rawTypeDefs, typeDef.getType());

                if (originalIndex == -1) {
                    var lineIndex = getExtendedTypeLineNumber(typeDef.getType(), lines);
                    var lineCol = constructLineAndColumnData(lines, lineIndex, typeDef.getType());

                    transformErrors.add(new ModuleTransformationSingleError(
                            String.format("extended type %s does not exist", typeDef.getType()),
                            filename,
                            lineCol[0],
                            lineCol[1]));
                    continue;
                }

                var original = rawTypeDefs.get(originalIndex);

                if (original.getRelations() == null || original.getRelations().isEmpty()) {
                    original.setRelations(typeDef.getRelations());

                    if (original.getMetadata() == null) {
                        original.setMetadata(new Metadata());
                    }

                    original.getMetadata()
                            .setRelations(
                                    typeDef.getMetadata() != null
                                            ? typeDef.getMetadata().getRelations()
                                            : null);

                    if (original.getMetadata().getRelations() != null) {
                        for (var relEntry :
                                original.getMetadata().getRelations().entrySet()) {
                            relEntry.getValue().setSourceInfo(new SourceInfo()._file(filename));
                        }
                    }

                    rawTypeDefs.set(originalIndex, original);
                    continue;
                }

                var existingRelationNames =
                        new ArrayList<>(original.getRelations().keySet());

                for (var relEntry : typeDef.getRelations().entrySet()) {
                    var name = relEntry.getKey();
                    var relation = relEntry.getValue();

                    if (existingRelationNames.contains(name)) {
                        var lineIndex = getRelationLineNumber(name, lines);
                        var lineCol = constructLineAndColumnData(lines, lineIndex, name);

                        transformErrors.add(new ModuleTransformationSingleError(
                                String.format("relation %s already exists on type %s", name, typeDef.getType()),
                                filename,
                                lineCol[0],
                                lineCol[1]));
                        continue;
                    }

                    // Find the relation metadata
                    RelationMetadata relationsMeta = null;
                    if (typeDef.getMetadata() != null && typeDef.getMetadata().getRelations() != null) {
                        relationsMeta = typeDef.getMetadata().getRelations().get(name);
                    }

                    if (relationsMeta != null) {
                        relationsMeta.setSourceInfo(new SourceInfo()._file(filename));
                    }

                    original.getRelations().put(name, relation);
                    if (original.getMetadata() == null) {
                        original.setMetadata(new Metadata());
                    }
                    if (original.getMetadata().getRelations() == null) {
                        original.getMetadata().setRelations(new HashMap<>());
                    }
                    original.getMetadata().getRelations().put(name, relationsMeta);
                }
            }
        }

        model.setTypeDefinitions(rawTypeDefs);
        model.setConditions(new HashMap<>(conditions));

        if (!transformErrors.isEmpty()) {
            throw new ModuleTransformationError(transformErrors);
        }

        return model;
    }

    private static int findTypeDefIndex(List<TypeDefinition> typeDefs, String typeName) {
        for (int i = 0; i < typeDefs.size(); i++) {
            if (typeDefs.get(i).getType().equals(typeName)) {
                return i;
            }
        }
        return -1;
    }

    static int getTypeLineNumber(String typeName, String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("type " + typeName)) {
                return i;
            }
        }
        return -1;
    }

    static int getExtendedTypeLineNumber(String typeName, String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("extend type " + typeName)) {
                return i;
            }
        }
        return -1;
    }

    static int getConditionLineNumber(String conditionName, String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("condition " + conditionName)) {
                return i;
            }
        }
        return -1;
    }

    static int getRelationLineNumber(String relation, String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("define " + relation)) {
                return i;
            }
        }
        return -1;
    }

    static StartEnd[] constructLineAndColumnData(String[] lines, int lineIndex, String symbol) {
        if (lines.length == 0 || lineIndex == -1) {
            return new StartEnd[] {new StartEnd(0, 0), new StartEnd(0, 0)};
        }

        var rawLine = lines[lineIndex];
        var wordIdx = rawLine.indexOf(symbol);
        if (wordIdx == -1) {
            wordIdx = 0;
        }

        return new StartEnd[] {new StartEnd(lineIndex, lineIndex), new StartEnd(wordIdx, wordIdx + symbol.length())};
    }

    private ModuleTransformer() {}
}
