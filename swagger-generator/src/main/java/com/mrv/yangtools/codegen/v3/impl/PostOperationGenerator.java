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
public class PostOperationGenerator extends OperationGenerator {
    private final boolean dropLastSegmentParameters;

    public PostOperationGenerator(PathSegment path, DataObjectRepo repo, boolean dropLastSegmentParameters) {
        super(path, repo);
        this.dropLastSegmentParameters = dropLastSegmentParameters;
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation post = dropLastSegmentParameters ? listOperation() : defaultOperation();
        post.description("creates " + getName(node));
        
        RequestBody requestBody = new RequestBody();
        Content bodyContent = new Content();
        
        MediaType mediaType = new MediaType();
        mediaType.schema(new Schema<>().$ref(getDefinitionId(node)));
        //TODO DM make it configurable like in old impl
        bodyContent.addMediaType("application/json", mediaType);
        bodyContent.addMediaType("application/xml", mediaType);
        requestBody.description(getName(node) + " to be added to list");
        requestBody.content(bodyContent);
        
        post.requestBody(requestBody);

        post.getResponses().addApiResponse("201", new ApiResponse().description("Object created"));
        post.getResponses().addApiResponse("409", new ApiResponse().description("Object already exists"));        
        
        return post;
    }

    private Operation listOperation() {
        Operation listOper = defaultOperation();
        listOper.setParameters(path.listParams());
        return listOper;
    }
}
