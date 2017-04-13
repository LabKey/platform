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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleHtmlViewDefinition;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCache.FileListenerResource;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCacheHandlerOld;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ModuleResourceCaches.CacheId;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Filter;
import org.labkey.api.util.Path;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.labkey.api.module.ModuleHtmlViewDefinition.HTML_VIEW_EXTENSION;
import static org.labkey.api.module.ModuleHtmlViewDefinition.VIEW_METADATA_EXTENSION;

/**
 * Cache for views provided by modules as simple HTML files (along with metadata from an associated .view.xml file) in
 * the /resources/views directory or /resources/assays/[provider name]/views.
 * User: adam
 * Date: 1/21/14
 */
public class ModuleHtmlViewCacheHandler implements ModuleResourceCacheHandler<Map<Path, ModuleHtmlViewDefinition>>
{
    // TODO: Push this into ModuleResourceCache()
    @Override
    public Map<Path, ModuleHtmlViewDefinition> load(@Nullable Resource dir, Module module, ModuleResourceCache<Map<Path, ModuleHtmlViewDefinition>> cache)
    {
        return load(new FileListenerResource(module.getModuleResource(Path.rootPath), module, cache), module);
    }

    @Override
    public Map<Path, ModuleHtmlViewDefinition> load(@Nullable Resource dir, Module module)
    {
        return getResourceRoots(module, dir, new Path(ModuleHtmlView.VIEWS_DIR));
    }

    private Map<Path, ModuleHtmlViewDefinition> getResourceRoots(Module module, @Nullable Resource resources, Path path)
    {
        List<Resource> roots = new LinkedList<>();

        if (null != resources)
        {
            Resource standardRoot = resources.find(path.getName());

            if (null != standardRoot && standardRoot.isCollection())
                roots.add(standardRoot);

            Resource assayRoot = resources.find("assay");

            if (null != assayRoot && assayRoot.isCollection())
            {
                for (Resource root : assayRoot.list())
                {
                    if (root.isCollection())
                    {
                        Resource r = root.find(path.getName());

                        if (null != r && r.isCollection())
                            roots.add(r);
                    }
                }
            }
        }

        return load(roots.stream(), module, resource -> resource.getName().endsWith(HTML_VIEW_EXTENSION));
    }

    private Map<Path, ModuleHtmlViewDefinition> load(Stream<Resource> roots, Module module, Filter<Resource> filter)
    {
        Map<Path, ModuleHtmlViewDefinition> map = new HashMap<>();

        roots.forEach(root -> {
            root.list().stream()
                .filter(resource -> resource.isFile() && filter.accept(resource))
                .forEach(resource -> {
                    ModuleHtmlViewDefinition def = new ModuleHtmlViewDefinition(resource);
                    map.put(resource.getPath(), def);
                });
        });

        return map.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }

    // Sample impl, if case we choose to auto-filter
    private Map<Path, ModuleHtmlViewDefinition> load2(Stream<Resource> files, Module module)
    {
        Map<Path, ModuleHtmlViewDefinition> map = new HashMap<>();

        files.forEach(file -> {
            ModuleHtmlViewDefinition def = new ModuleHtmlViewDefinition(file);
            map.put(file.getPath(), def);
        });

        return map.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }
}
