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

import com.mrv.yangtools.codegen.DataObjectRepo;
import com.mrv.yangtools.codegen.v3.PathSegment;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Simple command that generates operation
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public abstract class OperationGenerator<T extends SchemaNode & DataNodeContainer> {

    protected final PathSegment path;
    private final DataObjectRepo repo;

    protected OperationGenerator(PathSegment path, DataObjectRepo repo) {
        java.util.Objects.requireNonNull(path);
        java.util.Objects.requireNonNull(repo);
        this.path = path;
        this.repo = repo;
    }

    /**
     * Create operation for node.
     * @param node YANG node
     * @return Swagger operation
     */
    public Operation execute(DataSchemaNode node) {
        return defaultOperation();
    }

    protected String getDefinitionId(T node) {
        return repo.getDefinitionId(node);
    }

    protected String getName(T node) {
        return repo.getName(node);
    }

    /**
     * Default empty operation that defines error response only
     * @return basic operation with 400 response pre-defined
     */
    protected Operation defaultOperation() {
        final Operation operation = new Operation();
        ApiResponse apiResponse = new ApiResponse().description("Internal error");
        ApiResponses apiResponses = new ApiResponses();
        apiResponses.addApiResponse("400", apiResponse);
        operation.responses(apiResponses);
        operation.parameters(path.params());
        
        return operation;
    }
}
