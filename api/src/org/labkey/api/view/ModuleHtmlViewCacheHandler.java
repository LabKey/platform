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
import org.labkey.api.module.ModuleHtmlViewDefinition;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCacheHandlerOld;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ModuleResourceCaches.CacheId;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.module.ModuleHtmlViewDefinition.HTML_VIEW_EXTENSION;
import static org.labkey.api.module.ModuleHtmlViewDefinition.VIEW_METADATA_EXTENSION;

/**
 * Cache for views provided by modules as simple HTML files in their ./resources/views directory, along with
 * any associated metadata from corresponding a .view.xml file.
 * User: adam
 * Date: 1/21/14
 */
public class ModuleHtmlViewCacheHandler implements ModuleResourceCacheHandlerOld<Path, ModuleHtmlViewDefinition>
{
    @Override
    public boolean isResourceFile(String filename)
    {
        return filename.endsWith(HTML_VIEW_EXTENSION) || filename.endsWith(VIEW_METADATA_EXTENSION);
    }

    @Override  // Always return the .html filename as the resource name
    public String getResourceName(Module module, String filename)
    {
        if (filename.endsWith(HTML_VIEW_EXTENSION))
            return filename;

        if (filename.endsWith(VIEW_METADATA_EXTENSION))
            return filename.substring(0, filename.length() - VIEW_METADATA_EXTENSION.length()) + HTML_VIEW_EXTENSION;

        throw new IllegalStateException("Filename doesn't end in " + HTML_VIEW_EXTENSION + " or " + VIEW_METADATA_EXTENSION + ": \"" + filename + "\"");
    }

    @Override
    public String createCacheKey(Module module, Path path)
    {
        return ModuleResourceCaches.createCacheKey(module, path.toString());
    }

    @Override
    public CacheLoader<String, ModuleHtmlViewDefinition> getResourceLoader()
    {
        return (key, argument) ->
        {
            CacheId cid = ModuleResourceCaches.parseCacheKey(key);
            Path path = Path.parse(cid.getName());
            Resource r = cid.getModule().getModuleResource(path);

            return new ModuleHtmlViewDefinition(r);
        };
    }

    public static class ModuleHtmlViewCacheHandler2 implements ModuleResourceCacheHandler<Map<Path, ModuleHtmlViewDefinition>>
    {
        @Override
        public Map<Path, ModuleHtmlViewDefinition> load(@Nullable Resource dir, Module module)
        {
            if (null == dir)
                return Collections.emptyMap();

            Map<Path, ModuleHtmlViewDefinition> map = new HashMap<>();

            dir.list().stream()
                .filter(resource -> resource.isFile() && resource.getName().endsWith(HTML_VIEW_EXTENSION))
                .forEach(resource -> {
                    ModuleHtmlViewDefinition def = new ModuleHtmlViewDefinition(resource);
                    map.put(resource.getPath(), def);
                });

            return Collections.unmodifiableMap(map);
        }
    }
}
