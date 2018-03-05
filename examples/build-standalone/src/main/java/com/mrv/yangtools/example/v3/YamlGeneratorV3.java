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

package com.mrv.yangtools.example.v3;

import java.io.File;
import java.io.FileWriter;

import com.mrv.yangtools.codegen.v3.OpenAPIGenerator;
import com.mrv.yangtools.codegen.v3.impl.SegmentTagGenerator;

/**
 * Simple example of swagger generation for TAPI modules
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class YamlGeneratorV3 {

    public static void main(String[] args) throws Exception {
        OpenAPIGenerator generator;
        if(args.length == 1) {
            generator = GeneratorHelperV3.getGenerator(new File(args[0]),m -> true);
        } else {
            generator = GeneratorHelperV3.getGenerator(m -> m.getName().startsWith("Tapi"));
        }

        generator
                .tagGenerator(new SegmentTagGenerator())
                .elements(OpenAPIGenerator.Elements.RCP);
             //  .appendPostProcessor(new SingleParentInheritenceModel());


        generator.generate(new FileWriter("openAPI.yaml"));
//        generator.generate(new OutputStreamWriter(System.out));

    }
}
