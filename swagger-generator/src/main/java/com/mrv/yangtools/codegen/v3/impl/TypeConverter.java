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
import org.opendaylight.yangtools.yang.model.api.type.*;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.opendaylight.yangtools.yang.model.util.type.BaseTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mrv.yangtools.codegen.v3.DataObjectBuilder;

import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

/**
 * Supports type conversion between YANG and swagger
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class TypeConverter {

    private SchemaContext ctx;
    private DataObjectBuilder dataObjectBuilder;

    public TypeConverter(SchemaContext ctx) {
        this.ctx = ctx;
    }

    private static final Logger log = LoggerFactory.getLogger(TypeConverter.class);

    /**
     * Convert YANG type to OpenAPI schema property
     * @param type YANG
     * @param parent for scope computation (to support leafrefs)
     * @return property
     */
    public Schema<?> convert(TypeDefinition<?> type, SchemaNode parent) {
        TypeDefinition<?> baseType = type.getBaseType();
        if(baseType == null) baseType = type;

        if(type instanceof LeafrefTypeDefinition) {
            log.debug("leaf node {}",  type);
            baseType = SchemaContextUtil.getBaseTypeForLeafRef((LeafrefTypeDefinition) type, ctx, parent);
        }

        if(baseType instanceof BooleanTypeDefinition) {
            return new BooleanSchema();
        }

        if(baseType instanceof IntegerTypeDefinition || baseType instanceof UnsignedIntegerTypeDefinition) {
            //TODO [bmi] how to map int8 type ???
            IntegerSchema integer = new IntegerSchema();
            if (BaseTypes.isInt64(baseType) || BaseTypes.isUint32(baseType)) {
                integer.setFormat("int64");
            }
            return integer;
        }

        EnumTypeDefinition e = toEnum(type);
        if(e != null) {
            if(enumToModel()) {
                String refString = dataObjectBuilder.addSchema(e);
                Schema<String> refSchema = new Schema<>();
                refSchema.$ref(refString);
                return refSchema;
            }
        }

        return new StringSchema();
    }

    /**
     * Check if builder is present.
     * @return <code>true</code>
     * @throws IllegalStateException in case it is not present
     */
    protected boolean enumToModel() {
        if(dataObjectBuilder == null) throw new IllegalStateException("no data object builder configured");
        return true;
    }

    private EnumTypeDefinition toEnum(TypeDefinition<?> type) {
        if(type instanceof  EnumTypeDefinition) return (EnumTypeDefinition) type;
        if(type.getBaseType() instanceof  EnumTypeDefinition) return (EnumTypeDefinition) type.getBaseType();
        return null;
    }


    public void setDataObjectBuilder(DataObjectBuilder dataObjectBuilder) {
        this.dataObjectBuilder = dataObjectBuilder;
    }
}
