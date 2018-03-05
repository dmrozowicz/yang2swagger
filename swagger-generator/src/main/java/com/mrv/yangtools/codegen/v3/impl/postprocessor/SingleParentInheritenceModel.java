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

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SingleParentInheritenceModel implements Consumer<OpenAPI> {
    private static final Logger log = LoggerFactory.getLogger(SingleParentInheritenceModel.class);
    @Override
    public void accept(OpenAPI target) {
    	if (target.getComponents() != null && target.getComponents().getSchemas() != null) {
    		target.getComponents().getSchemas().entrySet().stream().filter(e -> {
	            Schema<?> m = e.getValue();
	            return m instanceof ComposedSchema
	                    && ((ComposedSchema) m).getAllOf().stream()
	                    .filter(c -> c.get$ref() != null).count() > 1;
	        }).forEach(e -> {
	        	ComposedSchema model = (ComposedSchema) e.getValue();
	            Schema<?> impl = (Schema<?>) model.getAllOf().stream()
	            		.filter(s -> s.get$ref() == null)
	            		//TODO DM is above subsitute for below old condition?
//	                    .filter(m ->  m instanceof ModelImpl)
	                    .findFirst().orElse(new Schema<>());
	
	            if(! model.getAllOf().contains(impl)) {
	                log.debug("Adding simple model for values to unpack -  {}", e.getKey());
	                //TODO DM what about child which is missing in ComposedSchema
//	                model.setChild(impl);
	            }
	//              //TODO DM what about parent which is missing in ComposedSchema
//	            List<RefModel> references = model.getAllOf().stream().filter(c -> c.get$ref() != null && !c.equals(model.getParent()))
//	                    .map(c -> (RefModel)c)
//	                    .collect(Collectors.toList());
//	
//	            List<Schema<?>> toUnpack = references.stream()
//	                    .map(r -> swagger.getDefinitions().get(r.getSimpleRef()))
//	                    .filter(m -> m instanceof ModelImpl)
//	                    .map(m -> (ModelImpl) m)
//	                    .collect(Collectors.toList());
//	
//	
//	            if (references.size() != toUnpack.size()) {
//	                log.warn("Cannot unpack references for {}. Only simple models supported. Skipping", e.getKey());
//	            }
//	
//	            log.debug("Unpacking {} models of {}", toUnpack.size(),  e.getKey());
//	
//	            toUnpack.forEach(m -> copyAttributes(impl, m));
	         //   model.getAllOf().removeAll(references);
	
	        });
    	}
    }

    private void copyAttributes(Schema<?> target, Schema<?> source) {
        //TODO may require property copying and moving x- extensions down to properties
    	source.getProperties().forEach((k,v) -> target.addProperties(k, v));
    }
}
