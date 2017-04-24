/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.view;

import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlViewDefinition;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.module.ModuleHtmlViewDefinition.HTML_VIEW_EXTENSION;

/**
 * Cache for views provided by modules as simple HTML files (along with metadata from an associated .view.xml file) in
 * the /resources/views directory or /resources/assays/[provider name]/views.
 * User: adam
 * Date: 1/21/14
 */
public class ModuleHtmlViewCacheHandler implements ModuleResourceCacheHandler<Map<Path, ModuleHtmlViewDefinition>>
{
    private static final Predicate<Resource> HTML_VIEW_FILTER = resource -> resource.getName().endsWith(HTML_VIEW_EXTENSION);

    public Map<Path, ModuleHtmlViewDefinition> load(Stream<Resource> roots, Module module)
    {
        return unmodifiable(roots
            .flatMap(root -> root.list().stream())
            .filter(Resource::isFile)
            .filter(HTML_VIEW_FILTER)
            .collect(Collectors.toMap(Resource::getPath, ModuleHtmlViewDefinition::new)));
    }

    // Sample impl, that applies auto-filtering
    private Map<Path, ModuleHtmlViewDefinition> load2(Stream<Resource> roots, Predicate<Resource> filter)
    {
        Stream<? extends Resource> resources = roots
            .flatMap(root -> root.list().stream())
            .filter(Resource::isFile)
            .filter(filter);

        return load2(resources);
    }

    // Sample impl, in case we choose to auto-filter
    private Map<Path, ModuleHtmlViewDefinition> load2(Stream<? extends Resource> files)
    {
        Map<Path, ModuleHtmlViewDefinition> map = files.collect(Collectors.toMap(Resource::getPath, ModuleHtmlViewDefinition::new));

        return map.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }
}
