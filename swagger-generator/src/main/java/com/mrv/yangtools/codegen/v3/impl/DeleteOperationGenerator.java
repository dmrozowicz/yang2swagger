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
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class DeleteOperationGenerator extends OperationGenerator {
    public DeleteOperationGenerator(PathSegment path, DataObjectRepo repo) {
        super(path, repo);
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation delete = defaultOperation();
        delete.description("removes " + getName(node));
        
        ApiResponse apiResponse = new ApiResponse().description("Object deleted");
        delete.getResponses().addApiResponse("204", apiResponse);

        return delete;
    }
}
