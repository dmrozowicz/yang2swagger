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

import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

import com.mrv.yangtools.codegen.DataObjectRepo;
import com.mrv.yangtools.codegen.v3.PathSegment;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class PutOperationGenerator extends OperationGenerator {
    public PutOperationGenerator(PathSegment path, DataObjectRepo repo) {
        super(path, repo);
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation put = defaultOperation();
        put.description("creates or updates " + getName(node));
        
        RequestBody requestBody = new RequestBody();
        Content bodyContent = new Content();
        
        MediaType mediaType = new MediaType();
        mediaType.schema(new Schema<>().$ref(getDefinitionId(node)));
        //TODO DM make it configurable like in old impl
        bodyContent.addMediaType("application/json", mediaType);
        bodyContent.addMediaType("application/xml", mediaType);
        requestBody.description(getName(node) + " to be added or updated");
        requestBody.content(bodyContent);
        
        put.requestBody(requestBody);

        put.getResponses().addApiResponse("201", new ApiResponse().description("Object created"));
        put.getResponses().addApiResponse("204", new ApiResponse().description("Object modified"));

        return put;
    }
}
