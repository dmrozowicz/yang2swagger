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

import static com.mrv.yangtools.common.BindingMapping.getClassName;
import static com.mrv.yangtools.common.BindingMapping.nameToPackageSegment;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchema;
import org.opendaylight.yangtools.yang.model.api.AugmentationTarget;
import org.opendaylight.yangtools.yang.model.api.ChoiceCaseNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DerivableSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DocumentedNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mrv.yangtools.codegen.impl.DataNodeHelper;
import com.mrv.yangtools.codegen.impl.ModuleUtils;
import com.mrv.yangtools.codegen.v3.DataObjectBuilder;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public abstract class AbstractDataObjectBuilder implements DataObjectBuilder {

    private static final Logger log = LoggerFactory.getLogger(AbstractDataObjectBuilder.class);

    protected static final String SCHEMA_REF_PREFIX = "#/components/schemas/";
    protected final OpenAPI openAPI;
    protected final TypeConverter converter;
    protected final SchemaContext ctx;
    protected final ModuleUtils moduleUtils;
    protected final Map<SchemaNode, String> names;
    private final HashMap<QName, String> generatedEnums;
    private final HashMap<DataNodeContainer, String> orgNames;

    protected final static Function<DataNodeContainer, Set<AugmentationSchema>> augmentations = node -> {
        if(node instanceof AugmentationTarget) {
            Set<AugmentationSchema> res = ((AugmentationTarget) node).getAvailableAugmentations();
            if(res != null) return res;
        }
        return Collections.emptySet();
    };

    protected final static Predicate<DataNodeContainer> isAugmented = n -> !augmentations.apply(n).isEmpty();

    protected final Predicate<DataNodeContainer> isTreeAugmented = n ->  n != null && (isAugmented.test(n) || n.getChildNodes().stream()
            .filter(c -> c instanceof DataNodeContainer)
            .anyMatch(c -> this.isTreeAugmented.test((DataNodeContainer) c)));

    public AbstractDataObjectBuilder(SchemaContext ctx, OpenAPI openAPI, TypeConverter converter) {
        this.names = new HashMap<>();
        this.converter = converter;
        converter.setDataObjectBuilder(this);
        this.openAPI = openAPI;
        this.ctx = ctx;
        this.moduleUtils = new ModuleUtils(ctx);
        this.generatedEnums = new HashMap<>();
        this.orgNames = new HashMap<>();
    }

    /**
     * Get definition id for node. Prerequisite is to have node's module traversed {@link UnpackingDataObjectsBuilder#processModule(Module)}.
     * @param node node
     * @return id
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> String getDefinitionId(T node) {
        return SCHEMA_REF_PREFIX + getName(node);
    }

    /**
     * Traverse model to collect all verbs from YANG nodes that will constitute Swagger models
     * @param module to traverse
     */
    @Override
    public void processModule(Module module) {
        HashSet<String> cache = new HashSet<>(names.values());
        log.debug("processing data nodes defined in {}", module.getName());
        processNode(module, cache);

        log.debug("processing rpcs defined in {}", module.getName());
        module.getRpcs().forEach(r -> {
            if(r.getInput() != null)
                processNode(r.getInput(), r.getQName().getLocalName() + "-input",  cache);
            if(r.getOutput() != null)
                processNode(r.getOutput(),r.getQName().getLocalName() + "-output", cache);
        });
        log.debug("processing augmentations defined in {}", module.getName());
        module.getAugmentations().forEach(r -> processNode(r, cache));
    }

    protected  void processNode(ContainerSchemaNode container, String proposedName, Set<String> cache) {
        if(container == null) return;
        String name = generateName(container, proposedName, cache);
        names.put(container, name);

        processNode(container, cache);
    }

    protected void processNode(DataNodeContainer container, Set<String> cache) {
        log.debug("DataNodeContainer string: {}", container.toString());
        DataNodeHelper.stream(container).filter(n -> n instanceof ContainerSchemaNode || n instanceof ListSchemaNode)
                .filter(n -> ! names.containsKey(n))
                .forEach(n -> {
                    String name = generateName(n, null, cache);
                    names.put(n, name);
                });
    }

    protected DataNodeContainer original(DataNodeContainer node) {
        DataNodeContainer result = null;
        DataNodeContainer tmp = node;
        do {
            if(tmp instanceof DerivableSchemaNode) {
                com.google.common.base.Optional<? extends SchemaNode> original = ((DerivableSchemaNode) tmp).getOriginal();
                tmp = null;
                if(original.isPresent() && original.get() instanceof DataNodeContainer) {
                    result = (DataNodeContainer) original.get();
                    tmp = result;
                }
            } else {
                tmp = null;
            }
        } while (tmp != null);

        return result;
    }

    protected String generateName(SchemaNode node, String proposedName, Set<String> cache) {
        if(node instanceof DataNodeContainer) {
            DataNodeContainer original = null;
            if(! isTreeAugmented.test((DataNodeContainer) node)) {
                original = original((DataNodeContainer) node);
            }

            if(original != null) {
                if(! orgNames.containsKey(original)) {
                    String name = generateName((SchemaNode)original, proposedName, cache);
                    orgNames.put(original, name);
                } else {
                    log.debug("reusing original definition to get name for {}", node.getQName());
                }

                return orgNames.get(original);
            } else {
                DataNodeContainer t = (DataNodeContainer) node;
                if(orgNames.containsKey(t)) {
                    return orgNames.get(t);
                }
            }
        }

        String modulePrefix =  nameToPackageSegment(moduleUtils.toModuleName(node.getQName()));
        if(proposedName != null) {
            return modulePrefix + "." + getClassName(proposedName);
        }

        String name = getClassName(node.getQName());
        final Iterable<QName> path = node.getPath().getParent().getPathFromRoot();
        if(path == null || !path.iterator().hasNext()) {
            log.debug("generatedName: {}", modulePrefix + "." + name);
            return modulePrefix + "." + name;
        }
        String pkg = StreamSupport.stream(path.spliterator(), false).map(n -> getClassName(n.getLocalName()).toLowerCase()).collect(Collectors.joining("."));
        log.debug("generatedName: {}", modulePrefix + "." + pkg + "." + name);
        return modulePrefix + "." + pkg + "." + name;
    }

    /**
     * Convert leaf-list to swagger property
     * @param llN leaf-list
     * @return property
     */
    protected Schema<?> getPropertyByType(LeafListSchemaNode llN) {
        return converter.convert(llN.getType(), llN);
    }

    /**
     * Convert leaf to swagger property
     * @param lN leaf
     * @return property
     */
    protected Schema<?> getPropertyByType(LeafSchemaNode lN) {

        final Schema<?> property = converter.convert(lN.getType(), lN);
        property.setDefault(lN.getDefault());

        return property;
    }


    protected Map<String, Schema> structure(DataNodeContainer node) {
        return structure(node,  x -> true, x -> true);
    }


    protected Map<String, Schema> structure(DataNodeContainer node, Predicate<DataSchemaNode> acceptNode, Predicate<DataSchemaNode> acceptChoice) {

        Predicate<DataSchemaNode> choiceP = c -> c instanceof ChoiceSchemaNode;

        // due to how inheritance is handled in yangtools the localName node collisions might appear
        // thus we need to apply collision strategy to override with the last attribute available
        Map<String, Schema<?>> properties = node.getChildNodes().stream()
                .filter(choiceP.negate().and(acceptNode)) // choices handled elsewhere
                .map(this::prop).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property, (oldV, newV) -> newV));

        Map<String,  Schema<?>> choiceProperties = node.getChildNodes().stream()
                .filter(choiceP.and(acceptChoice)) // handling choices
                .flatMap(c -> {
                    ChoiceSchemaNode choice = (ChoiceSchemaNode) c;
                    Stream<Pair> streamOfPairs = choice.getCases().stream()
                            .flatMap(_case -> _case.getChildNodes().stream().map(sc -> {
                                Pair prop = prop(sc);
                                assignCaseMetadata(prop.property, choice, _case);
                                return prop;
                            }));
                    return streamOfPairs;
                }).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property, (oldV, newV) -> newV));

        HashMap<String, Schema> result = new HashMap<>();

        result.putAll(properties);
        result.putAll(choiceProperties);
        return result;
    }

    protected Pair prop(DataSchemaNode node) {
        final String propertyName = getPropertyName(node);

        Schema<?> schema = null;

        if (node instanceof LeafListSchemaNode) {
            LeafListSchemaNode ll = (LeafListSchemaNode) node;
            schema = new ArraySchema().items(getPropertyByType(ll));
        } else if (node instanceof LeafSchemaNode) {
            LeafSchemaNode lN = (LeafSchemaNode) node;
            schema = getPropertyByType(lN);
        } else if (node instanceof ContainerSchemaNode) {
            schema = refOrStructure((ContainerSchemaNode) node);
        } else if (node instanceof ListSchemaNode) {
            schema = new ArraySchema().items(refOrStructure((ListSchemaNode) node));
        }

        if (schema != null) {
            schema.setDescription(desc(node));
        }

        return new Pair(propertyName, schema);
    }

    public String getPropertyName(DataSchemaNode node) {
        //return BindingMapping.getPropertyName(node.getQName().getLocalName());
        String name = node.getQName().getLocalName();
        if(node.isAugmenting()) {
            name = moduleName(node) + ":" + name;
        }
        return name;
    }

    private String moduleName(DataSchemaNode node) {
        Module module = ctx.findModuleByNamespaceAndRevision(node.getQName().getNamespace(), node.getQName().getRevision());
        return module.getName();
    }

    protected abstract <T extends DataSchemaNode & DataNodeContainer> Schema<?> refOrStructure(T node);

    private static void assignCaseMetadata(Schema<?> property, ChoiceSchemaNode choice, ChoiceCaseNode aCase) {
        String choiceName = choice.getQName().getLocalName();
        String caseName = aCase.getQName().getLocalName();

        ((Schema<?>) property).addExtension("x-choice", choiceName + ":" + caseName);
    }

    /**
     * Add model to referenced swagger for given node. All related models are added as well if needed.
     * @param node for which build a node
     * @param <T> type of the node
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> void addSchema(T node, String tagName) {


        Schema<?> schema = build(node);

        String modelName = getName(node);

        if(tagName != null) {
        	final Schema<String> schemaWrapper = new Schema<>();
        	schemaWrapper.addProperties(tagName, schema);
            schema = schemaWrapper;
        }
        
		if (openAPI.getComponents() != null 
				&& openAPI.getComponents().getSchemas() != null
				&& openAPI.getComponents().getSchemas().containsKey(modelName)) {
        	
            if(schema.equals(openAPI.getComponents().getSchemas().get(modelName))) {
                return;
            }
            log.warn("Overriding model {} with node {}", modelName, node.getQName());
        }

        openAPI.schema(modelName, schema);
    }

    @Override
    public <T extends SchemaNode & DataNodeContainer> void addSchema(T node) {
        addSchema(node, null);
    }

    @Override
    public String addSchema(EnumTypeDefinition enumType) {
        QName qName = enumType.getQName();
        
        //inline enumerations are a special case that needs extra enumeration
        if(qName.getLocalName().equals("enumeration") && enumType.getBaseType() == null) {
        	qName = QName.create(qName, enumType.getPath().getParent().getLastComponent().getLocalName() + "-" + qName.getLocalName());
        }

        if(! generatedEnums.containsKey(qName)) {
            log.debug("generating enum model for {}",  qName);
            String name = getName(qName);
            Schema<String> enumModel = build(enumType, qName);
            openAPI.schema(name, enumModel);
            generatedEnums.put(qName, SCHEMA_REF_PREFIX + name);
        } else {
            log.debug("reusing enum model for {}", enumType.getQName());
        }
        return generatedEnums.get(qName);
    }

    protected Schema<String> build(EnumTypeDefinition enumType, QName qName) {
    	Schema<String> schema = new Schema<>();
    	
        schema.setEnum(enumType.getValues().stream()
                .map(EnumTypeDefinition.EnumPair::getName).collect(Collectors.toList()));
        schema.setType("string");
        return schema;
    }

    protected String getName(QName qname) {
        String modulePrefix =  nameToPackageSegment(moduleUtils.toModuleName(qname));
        String name = modulePrefix + "." + getClassName(qname);

        String candidate = name;

        int idx = 1;
        while(generatedEnums.values().contains(SCHEMA_REF_PREFIX + candidate)) {
            log.warn("Name {} already defined for enum. generating random postfix", candidate);
            candidate = name + idx;
        }
        return candidate;
    }

    protected String desc(DocumentedNode node) {
        return  node.getReference() == null ? node.getDescription() :
                node.getDescription() + " REF:" + node.getReference();
    }

    protected static class Pair {
        final protected String name;
        final protected Schema<?> property;

        protected Pair(String name, Schema<?> property) {
            this.name = name;
            this.property = property;
        }
    }
}
