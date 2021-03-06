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

package com.mrv.yangtools.example;

import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.codegen.impl.SegmentTagGenerator;
import com.mrv.yangtools.codegen.impl.postprocessor.SingleParentInheritenceModel;

import java.io.*;

/**
 * Simple example of swagger generation for TAPI modules
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class YamlGenerator {

    public static void main(String[] args) throws Exception {
        SwaggerGenerator generator;
        if(args.length == 1) {
            generator = GeneratorHelper.getGenerator(new File(args[0]),m -> true);
        } else {
            generator = GeneratorHelper.getGenerator(m -> m.getName().startsWith("tapi"));
        }

        generator
                .tagGenerator(new SegmentTagGenerator())
                .elements(SwaggerGenerator.Elements.RCP)
                .appendPostProcessor(new SingleParentInheritenceModel());


        generator.generate(new FileWriter("swagger.yaml"));
//        generator.generate(new OutputStreamWriter(System.out));

    }
}
