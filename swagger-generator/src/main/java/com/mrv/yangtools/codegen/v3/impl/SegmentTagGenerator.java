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
import com.mrv.yangtools.codegen.v3.PathSegment;
import com.mrv.yangtools.codegen.v3.TagGenerator;
import com.mrv.yangtools.common.BindingMapping;

import java.util.*;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class SegmentTagGenerator implements TagGenerator {

    private final int level;

    public SegmentTagGenerator() {
        this(3);
    }

    public SegmentTagGenerator(int level) {
        this.level = level;
    }

    @Override
    public Set<String> tags(PathSegment segment) {
        Iterator<PathSegment> iterator = segment.iterator();

        LinkedList<String> names = new LinkedList<>();

        while(iterator.hasNext()) {
            names.addFirst(iterator.next().getName());
        }

        String name = names.size() > level ? names.get(level) : names.getLast();

        return new HashSet<>(Collections.singletonList(BindingMapping.getClassName(name)));
    }
}
