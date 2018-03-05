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
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class GetOperationGenerator extends OperationGenerator {
    public GetOperationGenerator(PathSegment path, DataObjectRepo repo) {
        super(path, repo);
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation get = defaultOperation();
        get.description("returns " + getName(node));
        ApiResponse apiResponse = new ApiResponse().description(getName(node));
        Content apiResponseContent = new Content();
        MediaType mediaType = new MediaType();
        mediaType.schema(new Schema<>().$ref(getDefinitionId(node)));
        //TODO DM make it configurable like in old impl
        apiResponseContent.addMediaType("application/json", mediaType);
        apiResponseContent.addMediaType("application/xml", mediaType);
        apiResponse.content(apiResponseContent);
        get.getResponses().addApiResponse("200", apiResponse);
          
        return get;
    }
}
