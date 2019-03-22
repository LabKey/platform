/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.module.ModuleHtmlViewDefinition.HTML_VIEW_EXTENSION;

/**
 * Cache for views provided by modules as simple HTML files (along with metadata from an associated .view.xml file) in
 * the /resources/views directory or /resources/assays/[provider name]/views.
 *
 * User: adam
 * Date: 1/21/14
 */
public class ModuleHtmlViewCacheHandler implements ModuleResourceCacheHandler<Map<Path, ModuleHtmlViewDefinition>>
{
    public Map<Path, ModuleHtmlViewDefinition> load(Stream<? extends Resource> resources, Module module)
    {
        return unmodifiable(resources
            .filter(getFilter(HTML_VIEW_EXTENSION))
            .collect(Collectors.toMap(Resource::getPath, ModuleHtmlViewDefinition::new)));
    }
}
