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

import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;

import io.swagger.v3.oas.models.media.Schema;

/**
 * Annotate property with metadata for leafref
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class AnnotatingTypeConverter extends TypeConverter {
    public AnnotatingTypeConverter(SchemaContext ctx) {
        super(ctx);
    }

    @Override
    public Schema<?> convert(TypeDefinition<?> type, SchemaNode parent) {
    	Schema prop = super.convert(type, parent);

        if(prop instanceof Schema) {
            if(type instanceof LeafrefTypeDefinition) {
                String leafRef = ((LeafrefTypeDefinition) type).getPathStatement().toString();
                ((Schema) prop).addExtension("x-path", leafRef);
            }
        }

        return prop;
    }
}
