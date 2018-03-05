/*
 * Copyright (c) 2016 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Christopher Murch <cmurch@mrv.com>
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.v3.impl;

import com.mrv.yangtools.codegen.impl.DataNodeHelper;
import com.mrv.yangtools.codegen.impl.GroupingHierarchyHandler;
import com.mrv.yangtools.common.BindingMapping;
import com.mrv.yangtools.common.Tuple;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.effective.GroupingEffectiveStatementImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The builder strategy is to reuse grouping wherever possible. Therefore in generated Swagger models, groupings are transformed to models
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class OptimizingDataObjectBuilder extends AbstractDataObjectBuilder {
    private static final Logger log = LoggerFactory.getLogger(OptimizingDataObjectBuilder.class);

    private HashMap<SchemaPath, GroupingDefinition> groupings;

    private Map<Object, Schema<?>> existingSchemas;
    private final GroupingHierarchyHandler groupingHierarchyHandler;
    private Map<Object, Set<UsesNode>> usesCache;

    private final Deque<DataNodeContainer> effectiveNode;

    public OptimizingDataObjectBuilder(SchemaContext ctx, OpenAPI openAPI, TypeConverter converter) {
        super(ctx, openAPI, converter);
        groupings = new HashMap<>();
        existingSchemas = new HashMap<>();
        usesCache = new HashMap<>();
        groupingHierarchyHandler = new GroupingHierarchyHandler(ctx);
        effectiveNode = new LinkedList<>();

        Set<Module> allModules = ctx.getModules();
        HashSet<String> names = new HashSet<>();
        allModules.forEach(m -> processGroupings(m, names));
    }

    @SuppressWarnings("unchecked")
    public <T extends SchemaNode & DataNodeContainer> Optional<T> effective(T node) {
        return effectiveNode.stream().filter(n -> {
            return n instanceof SchemaNode && ((SchemaNode) n).getQName().equals(node.getQName());
        }).map(n -> (T)n).findFirst();
    }


    @Override
    public <T extends SchemaNode & DataNodeContainer> String getName(T node) {
        if(isTreeAugmented.test(node)) {
            Optional<T> effective = effective(node);
            return effective.isPresent() ? names.get(effective.get()) : names.get(node);
        } else {
            DataNodeContainer toCheck = original(node) == null ? node : original(node);

            if(isDirectGrouping(toCheck)) {
                return names.get(grouping(toCheck));
            }
        }
        return names.get(node);
    }

    private <T extends SchemaNode & DataNodeContainer> T getEffectiveChild(QName name) {
        if(effectiveNode.isEmpty()) return null;
        return effectiveNode.stream().map(n -> n.getDataChildByName(name))
                .filter(n -> n instanceof DataNodeContainer)
                .map(n -> (T)n)
                .findFirst().orElse(null);
    }


    private Stream<GroupingDefinition> groupings(DataNodeContainer node) {
        Set<UsesNode> uses = uses(node);
        //noinspection SuspiciousMethodCalls
        return uses.stream().map(u -> groupings.get(u.getGroupingPath()));
    }

    private GroupingDefinition grouping(DataNodeContainer node) {
        Set<UsesNode> uses = uses(node);
        assert uses.size() == 1;
        //noinspection SuspiciousMethodCalls
        return groupings.get(uses.iterator().next().getGroupingPath());
    }

    /**
     * Is node that has no attributes only single grouping.
     * @param node to check
     * @return <code>true</code> if node is using single grouping and has no attributes
     */
    @SuppressWarnings("unchecked")
    private <T extends SchemaNode & DataNodeContainer> boolean isDirectGrouping(DataNodeContainer node) {

        if(node instanceof DataSchemaNode) {
            T n = (T) node;
            T effective = getEffectiveChild(n.getQName());
            if(effective == null) {
                if(! effectiveNode.isEmpty()) {
                    DataNodeContainer first = effectiveNode.getFirst();
                    if(first instanceof SchemaNode && ((SchemaNode) first).getQName().equals(n.getQName())) {
                        effective = (T) first;
                    }
                }
            }
            if(isTreeAugmented.test(effective)) return false;
        }

        if(isAugmented.test(node)) return false;

        Set<UsesNode> uses = uses(node);
        return uses.size() == 1 && node.getChildNodes().stream().filter(n -> !n.isAddedByUses()).count() == 0;
    }

    @Override
    protected void processNode(DataNodeContainer container, Set<String> cache) {
        final HashSet<String> used = new HashSet<String>(cache);

        DataNodeHelper.stream(container).filter(n -> n instanceof ContainerSchemaNode || n instanceof ListSchemaNode)
                .filter(n -> ! names.containsKey(n))
                .forEach(n -> {
                    String name = generateName(n, null, used);
                    used.add(name);
                    names.put(n, name);
                });
    }


    protected void processGroupings(DataNodeContainer container, Set<String> cache) {
        DataNodeHelper.stream(container).filter(n -> n instanceof GroupingDefinition)
                .map(n -> (GroupingDefinition)n)
                .forEach(n -> {
                    String gName = generateName(n, null, cache);
                    if(names.values().contains(gName)) {
                        //no type compatibility check at the moment thus this piece of code is prone to changes in parser

                        boolean differentDeclaration = groupings.values().stream().map(g -> ((GroupingEffectiveStatementImpl) g).getDeclared())
                                .noneMatch(g -> g.equals(((GroupingEffectiveStatementImpl) n).getDeclared()));
                        if(differentDeclaration) {
                            gName = "G" + gName;
                        }
                    }

                    names.put(n, gName);
                    groupings.put(n.getPath(), n);
                });
    }



    @SuppressWarnings("unchecked")
    @Override
    protected <T extends DataSchemaNode & DataNodeContainer> Schema<?> refOrStructure(T node) {
        String definitionId = getDefinitionId(node);
        T effectiveNode = (T) getEffectiveChild(node.getQName());

        boolean treeAugmented = isTreeAugmented.test(effectiveNode);

        if(treeAugmented) {
            definitionId = getDefinitionId(effectiveNode);
        }

        log.debug("reference to {}", definitionId);
        Schema<String> schema = new Schema<>().$ref(definitionId);

        if(treeAugmented && ! existingSchemas.containsKey(effectiveNode)) {
            log.debug("adding referenced model {} for node {} ", definitionId, effectiveNode);
            addSchema(effectiveNode);

        } else if(existingModel(node) == null) {
            log.debug("adding referenced model {} for node {} ", definitionId, node);
            addSchema(node);
        }

        return schema;
    }


    @Override
    public <T extends SchemaNode & DataNodeContainer> Schema<?> build(T node) {
        if(isTreeAugmented.test(node)) {
            return schema(node);
        }
        Schema<?> schema = existingModel(node);
        if(schema == null) {
            schema = schema(node);
        }

        return schema;
    }



    private List<DataNodeContainer> findRelatedNodes(DataNodeContainer node) {
        ArrayList<DataNodeContainer> result = new ArrayList<>();
        result.add(node);
        DataNodeContainer candidate = original(node);
        if(candidate != null) {
            result.add(candidate);
        } else {
            candidate = node;
        }

        if(isDirectGrouping(candidate)) {
            result.add(grouping(candidate));
        }
        return result;
    }

    private <T extends DataNodeContainer> Schema<?> existingModel(T node) {
        return findRelatedNodes(node).stream().map(n -> existingSchemas.get(n))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    /**
     * Create Schema for a node with zero of single uses
     * @param c to process
     * @return model or null in case more than one grouping is used
     */

    private Schema<?> fromContainer(DataNodeContainer c) {
        boolean simpleSchema;
        DataNodeContainer tmp;
        DataNodeContainer toModel = c;
        do {
            tmp = toModel;
            simpleSchema = uses(toModel).isEmpty() || isDirectGrouping(toModel);
            toModel = isDirectGrouping(toModel) ?  grouping(toModel) : toModel;
        } while(tmp != toModel && simpleSchema);

        return simpleSchema ? simple(toModel) : composed(toModel);
    }

    private Schema<?> fromAugmentation(AugmentationSchema augmentation) {

        Schema<?> schema = fromContainer(augmentation);
        final Schema<?> toCheck = schema;

        String existingId = null;
        if(openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            existingId = openAPI.getComponents().getSchemas().entrySet().stream().filter(e -> e.getValue().equals(toCheck)).map(Map.Entry::getKey)
                    .findFirst().orElse(null);        	
        }


        if(existingId != null) {
            Schema<?> ref = new Schema<>().$ref(existingId);
            ComposedSchema composedSchema = new ComposedSchema();
            composedSchema.addAllOfItem(ref);
            //TODO DM verify setChild vs allOf
            //composedSchema.setChild(ref);
            schema = composedSchema;
        }

        HashMap<String, String> properties = new HashMap<>();

        if(augmentation instanceof NamespaceRevisionAware) {
            URI uri = ((NamespaceRevisionAware) augmentation).getNamespace();
            properties.put("namespace", uri.toString());
            properties.put("prefix", moduleUtils.toModuleName(uri));
            schema.addExtension("x-augmentation", properties);
        }


        return schema;
    }

    @SuppressWarnings("unchecked")
    private <T extends SchemaNode & DataNodeContainer> Schema<?> schema(T node) {
        if(effectiveNode.isEmpty()) {
            effectiveNode.addFirst(node);
        } else {
            T effectiveChild = getEffectiveChild(node.getQName());
            if(effectiveChild == null) {
                log.warn("no child found with name {}", node.getQName());
                effectiveNode.addFirst(node);
            } else {
                effectiveNode.addFirst(effectiveChild);
            }

        }
        DataNodeContainer original = original(node);
        T toModel =  node;
        if(original instanceof SchemaNode) {
            toModel = (T) original;
        }

        Schema<?> schema = fromContainer(toModel);

        assert original == null || !isAugmented.test(original);
        if(isAugmented.test(node)) {
            String modelName = getName(toModel);
            int lastSegment = modelName.lastIndexOf(".") + 1;
            assert lastSegment > 0 : "Expecting name convention from name generator";
            modelName = modelName.substring(lastSegment);

            log.debug("processing augmentations for {}", node.getQName().getLocalName());
            //TODO order models
            List<Schema<?>> schemas = augmentations.apply(node).stream().map(this::fromAugmentation).collect(Collectors.toList());

            ComposedSchema augmented = new ComposedSchema();
            if(schema instanceof ComposedSchema) {
                augmented = (ComposedSchema) schema;
            } else {
                augmented.setDescription(schema.getDescription());
                schema.setDescription("");
                augmented.addAllOfItem(schema);
                //TODO DM verify above child vs addAllOfItem
                //augmented.child(schema);
            }

            LinkedList<Schema<?>> aSchemas = new LinkedList<Schema<?>>();
            //TODO DM investigate interfaces 
//            if(augmented.getInterfaces() != null) {
//                aSchemas.addAll(augmented.getInterfaces());
//            }
            int idx = 1;
            for(Schema<?> s : schemas) {
                Map<String, String> prop = (Map<String, String>) s.getExtensions().getOrDefault("x-augmentation", Collections.emptyMap());
                String pkg = BindingMapping.nameToPackageSegment(prop.get("prefix"));
                String augName = pkg + "." + modelName + "Augmentation" + idx;
                openAPI.schema(augName, s);
//                aSchemas.add(new RefModel("#/definitions/"+augName));
                aSchemas.add(new Schema<>().$ref("#/components/schemas/"+augName));
                idx++;

            }
            //TODO DM investigate interfaces 
            //augmented.setInterfaces(aSchemas);
            schema = augmented;
        }

        existingSchemas.put(toModel, schema);
        existingSchemas.put(node, schema);

        Optional<DataNodeContainer> toRemove = effectiveNode.stream().filter(
                n -> n instanceof SchemaNode && ((SchemaNode) n).getQName().equals(node.getQName()))
                .findFirst();

        toRemove.ifPresent(effectiveNode::remove);

        return schema;
    }

    private Set<UsesNode> uses(DataNodeContainer toModel) {
        if(usesCache.containsKey(toModel)) {
            return usesCache.get(toModel);
        }
        final Set<UsesNode> uses = new HashSet<>(toModel.getUses());
        Set<UsesNode> result = uses;

        if(result.size() > 1) {
            result = optimizeInheritance(uses);
        }
        usesCache.put(toModel, result);
        return result;
    }

    private Set<UsesNode> optimizeInheritance(Set<UsesNode> result) {
        return result.stream().filter(r -> {
            SchemaPath rName = r.getGroupingPath();

            return result.stream().filter(o -> ! o.equals(r))
                    .noneMatch(o -> groupingHierarchyHandler.isParent(rName, o.getGroupingPath().getLastComponent()));
        }).collect(Collectors.toSet());
    }

    @Override
    public <T extends SchemaNode & DataNodeContainer> void addSchema(T node) {
        super.addSchema(node);
    }


    private static class GroupingInfo {
        final Set<GroupingDefinition> models;
        final Set<QName> attributes;

        private GroupingInfo() {
            attributes = new HashSet<>();
            models = new HashSet<>();
        }
        private void addUse(GroupingInfo u) {
            attributes.addAll(u.attributes);
            models.addAll(u.models);
        }
    }

    private GroupingInfo traverse(GroupingDefinition def) {
        GroupingInfo info = groupings(def).map(this::traverse)
                .reduce(new GroupingInfo(), (o, cgi) -> {
                    o.addUse(cgi);
                    return o;
                });
        boolean tryToReuseGroupings = info.attributes.isEmpty();
        if(tryToReuseGroupings) {
            boolean noneAugmented = def.getChildNodes().stream().filter(c -> !c.isAddedByUses())
                    .allMatch(c -> {
                        DataSchemaNode effectiveChild = getEffectiveChild(c.getQName());
                        return ! OptimizingDataObjectBuilder.isAugmented.test((DataNodeContainer) effectiveChild);
                    });
            if(noneAugmented) {
                String groupingIdx = getDefinitionId(def);
                log.debug("found grouping id {} for {}", groupingIdx, def.getQName());
                info.models.clear();
                info.models.add(def);
            } else tryToReuseGroupings = false;

        }
        if(! tryToReuseGroupings) {
            def.getChildNodes().stream().filter(c -> !c.isAddedByUses())
                    .forEach(c -> info.attributes.add(c.getQName()));
        }
        return info;
    }



    private ComposedSchema composed(DataNodeContainer node) {
        ComposedSchema newSchema = new ComposedSchema();

        final Set<QName> fromAugmentedGroupings = new HashSet<>();
        final List<ObjectSchema> schemas = new LinkedList<>();

        uses(node).forEach(u -> {
            GroupingDefinition grouping = groupings.get(u.getGroupingPath());
            GroupingInfo info = traverse(grouping);
            info.models.forEach(def -> {
                String groupingIdx = getDefinitionId(def);
                log.debug("adding grouping {} to composed model", groupingIdx);
                ObjectSchema refSchema = new ObjectSchema();
                refSchema.set$ref(groupingIdx);

                if (existingModel(def) == null) {
                    log.debug("adding model {} for grouping", groupingIdx);
                    addSchema(def);
                }
                schemas.add(refSchema);
            });
            fromAugmentedGroupings.addAll(new ArrayList<>(info.attributes));
        });

        List<ObjectSchema> optimizedModels = optimizeInheritance(schemas);

        SchemaNode doc = null;
        if(node instanceof SchemaNode) {
            doc = (SchemaNode) node;
        }

        if(optimizedModels.size() > 1 && doc != null) {
            log.warn("Multiple inheritance for {}", doc.getQName());
        }
        // XXX this behavior might be prone to future changes to Swagger model
        // currently interfaces are stored as well in allOf property
        //TODO DM investigate interfaces, child, parent
//        newSchema.setInterfaces(optimizedModels);
//        if(!schemas.isEmpty())
//            newSchema.parent(schemas.get(0));

        // because of for swagger model order matters we need to add attributes at the end
        final ObjectSchema attributes = new ObjectSchema();
        if(doc != null)
            attributes.description(desc(doc));
        attributes.setProperties(structure(node, n -> fromAugmentedGroupings.contains(n.getQName()) ));
        //attributes.setDiscriminator("objType");
        boolean noAttributes = attributes.getProperties() == null || attributes.getProperties().isEmpty();
        if(! noAttributes) {
        	//TODO DM investigate interfaces, child, parent
            //newSchema.child(attributes);
        }

        if(schemas.size() == 1 && noAttributes) {
            log.warn("should not happen to have such object for node {}", node);
        }

        return newSchema;
    }

    private List<ObjectSchema> optimizeInheritance(List<ObjectSchema> schemas) {
        if(schemas.size() < 2) return schemas;
        Map<ObjectSchema, Set<String>> inheritance = schemas.stream()
                .map(m -> new RefSchemaTuple(m, inheritanceId(m)))
                .collect(Collectors.toMap(RefSchemaTuple::first, RefSchemaTuple::second, (v1, v2) -> {v1.addAll(v2); return v1;}));

        //duplicates
        HashSet<String> nameCache = new HashSet<>();
        List<ObjectSchema> resultingModels = schemas.stream().filter(m -> {
        	
        	//TODO DM investigate ref vs simple ref
            //String sn = m.getSimpleRef();
        	String sn = m.get$ref();
            boolean result = !nameCache.contains(sn);
            if (!result && log.isDebugEnabled()) {
                log.debug("duplicated models with name {}", sn);
            }
            nameCache.add(sn);
            return result;
        })
                // inheritance structure
                .filter(schema -> {
                    Set<String> mine = inheritance.get(schema);

                    // we leave only these models for which there is none more specific
                    // so if exist at least one more specific we can remove model
                    boolean existsMoreSpecific = inheritance.entrySet().stream()
                            .filter(e -> !e.getKey().equals(schema))
                            .map(e -> moreSpecific(e.getValue(), mine))
                            .filter(eMoreSpecific -> eMoreSpecific).findFirst().orElse(false);

                    if (existsMoreSpecific && log.isDebugEnabled()) {
                    	//TODO DM investigate  ref vs simpleref
                        log.debug("more specific models found than {}", schema.get$ref());
                    }
                    return !existsMoreSpecific;
                }).collect(Collectors.toList());

        if(resultingModels.size() != schemas.size()) {
            log.debug("optimization succeeded from {} to {}", schemas.size(), resultingModels.size());
        }
        return resultingModels;
    }

    /**
     * All of 'mine' strings are more specific than 'yours'.
     * In  other words for each of 'yours' exists at least one 'mine' which is more specific
     * @param mine mine ids
     * @param yours you
     * @return <code>true</code> if more specific
     */
    protected static boolean moreSpecific(Set<String> mine, Set<String> yours) {
        return yours.stream()
                .map(yString -> mine.stream()
                        .map(mString -> mString.contains(yString))
                        //exist in mine at least one string that contain yString [1]
                        .filter(contains -> contains).findFirst().orElse(false)
                )
                //does not exist any your string that is incompatible with (1)
                .filter(x -> !x).findFirst().orElse(true);
    }

    private Set<String> inheritanceId(ObjectSchema m) {

    	//TODO DM ivestigate simple ref
//        String id = m.getSimpleRef();
    	String id = m.get$ref();
    	//TODO DM revisit original conditions
        Schema<?> schema = openAPI.getComponents().getSchemas().get(id);
        if(schema instanceof ComposedSchema) {
            return ((ComposedSchema) schema).getAllOf().stream()
                    .filter(c -> c.get$ref() != null)
                    .flatMap(c -> inheritanceId((ObjectSchema)c)
                            .stream().map(s -> id + s)
                    ).collect(Collectors.toSet());
        }
        
        
        if(schema.get$ref() == null) {
        	return Collections.singleton(id);
        } else {
			return inheritanceId((ObjectSchema) schema).stream().map(s -> id + s).collect(Collectors.toSet());
		}

//        throw new IllegalArgumentException("model type not supported for " + id);
    }

    private static class RefSchemaTuple extends Tuple<ObjectSchema, Set<String>> {
        private RefSchemaTuple(ObjectSchema schema, Set<String> keys) {
            super(schema, keys);
        }
    }

    private  Schema<?> simple(DataNodeContainer toModel) {
        final Schema<?> schema = new Schema<>();
        if(schema instanceof DocumentedNode) {
            schema.description(desc((DocumentedNode) toModel));
        }
        log.debug("added object type for {}", toModel.toString());
        schema.setType("object");
        schema.setProperties(structure(toModel));
        return schema;
    }

    @Override
    protected Map<String, Schema> structure(DataNodeContainer node) {
        Predicate<DataSchemaNode> toSimpleProperty = d ->  !d.isAugmenting() && ! d.isAddedByUses();
        return super.structure(node, toSimpleProperty, toSimpleProperty);
    }

    protected Map<String, Schema> structure(DataNodeContainer node, Predicate<DataSchemaNode> includeAttributes) {
        Predicate<DataSchemaNode> toSimpleProperty = d ->  !d.isAugmenting() && ! d.isAddedByUses();
        return super.structure(node, toSimpleProperty.or(includeAttributes), toSimpleProperty.or(includeAttributes));
    }
}
