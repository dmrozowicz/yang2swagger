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

import com.mrv.yangtools.common.Tuple;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public class ReplaceEmptyWithParent implements Consumer<OpenAPI> {
	private final Logger log = LoggerFactory.getLogger(ReplaceEmptyWithParent.class);

	@Override
	public void accept(OpenAPI target) {
		if (target.getComponents() != null && target.getComponents().getSchemas() != null) {
			Map<String, String> replacements = target.getComponents().getSchemas().entrySet().stream().filter(e -> {
				Schema model = e.getValue();
				if (model instanceof ComposedSchema) {
					List<Schema> allOf = ((ComposedSchema) model).getAllOf();
					return allOf.size() == 1 && (allOf.get(0).get$ref() != null);
				}
				return false;
			}).map(e -> {
				Schema<?> ref = ((ComposedSchema) e.getValue()).getAllOf().get(0);

				return new Tuple<>(e.getKey(), ref.get$ref());

			}).collect(Collectors.toMap(Tuple::first, Tuple::second));

			log.debug("{} replacement found for definitions", replacements.size());
			log.trace("replacing paths");
			target.getPaths().values().stream().flatMap(p -> p.readOperations().stream())
					.forEach(o -> fixOperation(o, replacements));

			target.getComponents().getSchemas().forEach((key, value) -> fixModel(key, value, replacements));
			replacements.keySet().forEach(r -> {
				log.debug("removing {} model from swagger definitions", r);
				target.getComponents().getSchemas().remove(r);
			});
		}
	}

	private void fixModel(String name, Schema<?> m, Map<String, String> replacements) {
		ObjectSchema fixProperties = null;
		if (m instanceof ObjectSchema) {
			fixProperties = (ObjectSchema) m;
		}

		if (m instanceof ComposedSchema) {
			ComposedSchema cm = (ComposedSchema) m;
			fixComposedModel(name, cm, replacements);
			fixProperties = cm.getAllOf().stream().filter(c -> c instanceof ObjectSchema).map(c -> (ObjectSchema) c)
					.findFirst().orElse(null);
		}

		if (fixProperties == null)
			return;
		if (fixProperties.getProperties() == null) {
			// TODO we might also remove this one from definitions
			log.warn("Empty model in {}", name);
			return;
		}
		fixProperties.getProperties().forEach((key, value) -> {
			if (value.get$ref() != null) {
				if (fixProperty(value, replacements)) {
					log.debug("fixing property {} of {}", key, name);
				}
			} else if (value instanceof ArraySchema) {
				Schema<?> items = ((ArraySchema) value).getItems();
				if (items.get$ref() != null) {
					if (fixProperty(items, replacements)) {
						log.debug("fixing property {} of {}", key, name);
					}
				}
			}
		});

	}

	private boolean fixProperty(Schema<?> p, Map<String, String> replacements) {
		//TODO DM ref vs simple ref
		if (replacements.containsKey(p.get$ref())) {
			p.set$ref(replacements.get(p.get$ref()));
			return true;
		}
		return false;
	}

	private void fixComposedModel(String name, ComposedSchema m, Map<String, String> replacements) {
		//TODO DM ref vs simple ref
		Set<Schema<?>> toReplace = m.getAllOf().stream().filter(c -> c.get$ref() != null)
				.filter(rm -> replacements.containsKey(rm.get$ref())).collect(Collectors.toSet());
		toReplace.forEach(r -> {
			int idx = m.getAllOf().indexOf(r);
			Schema<?> newRef = new Schema<>().$ref(replacements.get(r.get$ref()));
			m.getAllOf().set(idx, newRef);
			//TODO DM interfaces ??
//			if (m.getInterfaces().remove(r)) {
//				m.getInterfaces().add(newRef);
//			}
		});
	}

	private void fixOperation(Operation operation, Map<String, String> replacements) {
		operation.getResponses().values().forEach(r -> fixResponse(r, replacements));
		fixParameter(operation.getRequestBody(), replacements);
		
		Optional<Map.Entry<String, String>> rep = replacements.entrySet().stream()
				.filter(r -> operation.getDescription() != null && operation.getDescription().contains(r.getKey()))
				.findFirst();
		if (rep.isPresent()) {
			log.debug("fixing description for '{}'", rep.get().getKey());
			Map.Entry<String, String> entry = rep.get();
			operation.setDescription(operation.getDescription().replace(entry.getKey(), entry.getValue()));
		}

	}

	private void fixParameter(RequestBody requestBody, Map<String, String> replacements) {
		if(requestBody == null || requestBody.getContent() == null) {
			return;
		}
		if (!(requestBody.getContent().values().iterator().next().getSchema().get$ref() != null))
			return;
		Schema<?> ref = requestBody.getContent().values().iterator().next().getSchema();
		if (replacements.containsKey(ref.get$ref())) {
			String replacement = replacements.get(ref.get$ref());
			requestBody.setDescription(requestBody.getDescription().replace(ref.get$ref(), replacement));
			requestBody.getContent().values().iterator().next().setSchema(new Schema<>().$ref(replacement));
		}

	}

	private void fixResponse(ApiResponse r, Map<String, String> replacements) {
		//TODO DM null check, can we assume that there is only one media type?
		if(r.getContent() == null) {
			return;
		}
		if (!(r.getContent().values().iterator().next().getSchema().get$ref() != null)) {
			return;
		}
		Schema<?> schema = r.getContent().values().iterator().next().getSchema();
		if (replacements.containsKey(schema.get$ref())) {
			String replacement = replacements.get(schema.get$ref());
			if (r.getDescription() != null)
				r.setDescription(r.getDescription().replace(schema.get$ref(), replacement));
			schema.setDescription(replacement);
			r.getContent().values().iterator().next().setSchema(new Schema<>().$ref(replacement));
		}

	}
}
