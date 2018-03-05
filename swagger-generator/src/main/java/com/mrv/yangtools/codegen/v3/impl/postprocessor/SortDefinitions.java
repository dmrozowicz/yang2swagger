/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */
package com.mrv.yangtools.codegen.v3.impl.postprocessor;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;

import java.util.function.Consumer;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SortDefinitions implements Consumer<OpenAPI> {
    @Override
    public void accept(OpenAPI target) {
    	if (target.getComponents() != null && target.getComponents().getSchemas() != null) {
            target.getComponents().getSchemas().values().stream()
            .filter(d -> d instanceof ComposedSchema)
            .forEach(d -> {
            	ComposedSchema m = (ComposedSchema) d;

                m.getAllOf().sort((a,b) -> {
                    if(a.get$ref() != null) {
                        if(b.get$ref() != null) {
                        	//TODO DM simpleref vs ref ?
                            return a.get$ref().compareTo(b.get$ref());
                        }

                    }
                    if(b.get$ref() != null) return 1;
                    //preserve the order for others
                    return -1;
                });

            });    		
    	}
    }
}
