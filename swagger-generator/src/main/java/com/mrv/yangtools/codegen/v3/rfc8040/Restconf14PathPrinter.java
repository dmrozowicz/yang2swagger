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

package com.mrv.yangtools.codegen.v3.rfc8040;


import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mrv.yangtools.codegen.v3.PathPrinter;
import com.mrv.yangtools.codegen.v3.PathSegment;

import io.swagger.v3.oas.models.parameters.Parameter;

/**
 * {@link PathPrinter} compliant with https://tools.ietf.org/html/draft-ietf-netconf-restconf-14#section-3.3
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class Restconf14PathPrinter extends PathPrinter {

    private static final Function<Collection<? extends Parameter>, String> param =
            params -> params.isEmpty() ? "/" :
                    "=" + params.stream().map(p -> "{" + p.getName() + "}").collect(Collectors.joining(",")) + "/";

    private final boolean useModuleName;

    public Restconf14PathPrinter(PathSegment path, boolean useModuleName) {
        this(path, useModuleName, false);
    }

    public Restconf14PathPrinter(PathSegment path, boolean useModuleName, boolean dropLastParams) {
        super(path, param, dropLastParams ? x -> "/" : param);
        this.useModuleName = useModuleName;
    }

    @Override
    public String segment() {
        return segment(paramPrinter, path.getModuleName(), path);

    }

    protected String segment(Function<Collection<? extends Parameter>, String> paramWriter, String moduleName, PathSegment seg) {
        if(seg.getName() == null) return "";
        return (useModuleName && moduleName != null && !moduleName.isEmpty() ? moduleName + ":" : "") + seg.getName() + paramWriter.apply(seg.getParam());
    }

    /**
     *
     * @return for full path
     */
    @Override
    public String path() {
        LinkedList<PathSegment> result = new LinkedList<>();

        PathSegment parent = path.drop();

        String lastSegment = segment(lastParamPrinter, path.getModuleName(), path);

        for(PathSegment p : parent) {
            result.addFirst(p);
        }

        return result.stream().map(s -> segment(paramPrinter, s.getModuleName(), s)).collect(Collectors.joining()) + lastSegment;

    }

}
