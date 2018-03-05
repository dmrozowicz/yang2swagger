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

package com.mrv.yangtools.codegen.v3.rfc8040;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import com.mrv.yangtools.codegen.v3.DataObjectBuilder;
import com.mrv.yangtools.codegen.v3.PathSegment;
import com.mrv.yangtools.codegen.v3.TagGenerator;
import com.mrv.yangtools.codegen.v3.impl.DeleteOperationGenerator;
import com.mrv.yangtools.codegen.v3.impl.GetOperationGenerator;
import com.mrv.yangtools.codegen.v3.impl.PostOperationGenerator;
import com.mrv.yangtools.codegen.v3.impl.PutOperationGenerator;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * REST path handler compliant with RESTCONF spec RFC 8040
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
class PathHandler implements com.mrv.yangtools.codegen.v3.PathHandler {
    private final OpenAPI target;
    private final SchemaContext ctx;

    private final String data;
    private final String operations;
    private final Module module;
    private final DataObjectBuilder dataObjectBuilder;
    private final Set<TagGenerator> tagGenerators;
    private final  boolean fullCrud;

    PathHandler(SchemaContext ctx, Module modules, OpenAPI target, DataObjectBuilder objBuilder, Set<TagGenerator> generators, boolean fullCrud) {
        this.target = target;
        this.ctx = ctx;
        this.module = modules;
        data = "/data/";
        operations = "/operations/";
        this.dataObjectBuilder = objBuilder;
        this.tagGenerators = generators;
        this.fullCrud = fullCrud;
    }


    @Override
    public void path(ContainerSchemaNode cN, PathSegment pathCtx) {
        final PathItem pathItem = operations(cN, pathCtx);
        //TODO pluggable PathPrinter
        Restconf14PathPrinter printer = new Restconf14PathPrinter(pathCtx, false);
        
        target.path(data + printer.path(), pathItem);
    }

    protected PathItem operations(DataSchemaNode node, PathSegment pathCtx) {
        final PathItem pathItem = new PathItem();
        List<String> tags = tags(pathCtx);
        tags.add(module.getName());
        
        pathItem.get(new GetOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
        if(fullCrud && !pathCtx.isReadOnly()) {
            pathItem.put(new PutOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
            pathItem.post(new PostOperationGenerator(pathCtx, dataObjectBuilder, false).execute(node).tags(tags));
            pathItem.delete(new DeleteOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
        }

        return pathItem;
    }

    @Override
    public void path(ListSchemaNode lN, PathSegment pathCtx) {
        final PathItem pathItem = operations(lN, pathCtx);

        List<String> tags = tags(pathCtx);
        tags.add(module.getName());

        Restconf14PathPrinter printer = new Restconf14PathPrinter(pathCtx, false);
        target.path(data + printer.path(), pathItem);

        //yes I know it can be written in previous 'if statement' but at some point it is to be refactored
        if(!fullCrud || pathCtx.isReadOnly()) return;


        //add list pathItem
        final PathItem list = new PathItem();
        list.post(new PostOperationGenerator(pathCtx, dataObjectBuilder, true).execute(lN));


        Restconf14PathPrinter postPrinter = new Restconf14PathPrinter(pathCtx, false, true);
        target.path(data + postPrinter.path(), list);
    }

    @Override
    public void path(ContainerSchemaNode input, ContainerSchemaNode output, PathSegment pathCtx) {
        final Restconf14PathPrinter printer = new Restconf14PathPrinter(pathCtx, false);

        Operation post = defaultOperation(pathCtx);

        post.addTagsItem(module.getName());
        if(input != null) {
            dataObjectBuilder.addSchema(input, "input");
            RequestBody requestBody = new RequestBody();
            Content bodyContent = new Content();
            
            MediaType mediaType = new MediaType();
            mediaType.schema(new Schema<>().$ref(dataObjectBuilder.getDefinitionId(input)));
            //TODO DM make it configurable like in old impl
            bodyContent.addMediaType("application/json", mediaType);
            bodyContent.addMediaType("application/xml", mediaType);
            requestBody.description(input.getDescription());
            requestBody.content(bodyContent);
            
            post.requestBody(requestBody);
        }

        if(output != null) {
            String description = output.getDescription();
            if(description == null) {
                description = "Correct response";
            }

            dataObjectBuilder.addSchema(output, "output");
            ApiResponse apiResponse = new ApiResponse().description(description);
            Content apiResponseContent = new Content();
            MediaType mediaType = new MediaType();
            mediaType.schema(new Schema<>().$ref(dataObjectBuilder.getDefinitionId(output)));
            //TODO DM make it configurable like in old impl
            apiResponseContent.addMediaType("application/json", mediaType);
            apiResponseContent.addMediaType("application/xml", mediaType);
            apiResponse.content(apiResponseContent);
            post.getResponses().addApiResponse("200", apiResponse);
 
        }
        post.getResponses().addApiResponse("201", new ApiResponse().description("No response")); //no output body
        target.path(operations + printer.path(), new PathItem().post(post));
    }

    private List<String> tags(PathSegment pathCtx) {
        List<String> tags = new ArrayList<>(tagGenerators.stream().flatMap(g -> g.tags(pathCtx).stream())
                .collect(Collectors.toSet()));
        Collections.sort(tags);
        return tags;
    }

    private Operation defaultOperation(PathSegment pathCtx) {
        final Operation operation = new Operation();
        ApiResponse apiResponse = new ApiResponse().description("Internal error");
        ApiResponses apiResponses = new ApiResponses();
        apiResponses.addApiResponse("400", apiResponse);
        operation.responses(apiResponses);
        operation.parameters(pathCtx.params());
        
        return operation;        
    }
}
