/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlViewDefinition;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import static org.labkey.api.module.ModuleHtmlViewDefinition.HTML_VIEW_EXTENSION;
import static org.labkey.api.module.ModuleHtmlViewDefinition.VIEW_METADATA_EXTENSION;

/**
 * User: adam
 * Date: 1/21/14
 * Time: 9:47 PM
 */
public class ModuleHtmlViewCacheHandler implements ModuleResourceCacheHandler<ModuleHtmlViewDefinition>
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
    public String createCacheKey(Module module, String resourceName)
    {
        return ModuleResourceCache.createCacheKey(module, resourceName);
    }

    @Override
    public CacheLoader<String, ModuleHtmlViewDefinition> getResourceLoader()
    {
        return new CacheLoader<String, ModuleHtmlViewDefinition>()
        {
            @Override
            public ModuleHtmlViewDefinition load(String key, @Nullable Object argument)
            {
                Pair<Module, String> pair = ModuleResourceCache.parseCacheKey(key);
                Path path = Path.parse(pair.second);
                Resource r = pair.first.getModuleResource(path);

                return new ModuleHtmlViewDefinition(r);
            }
        };
    }

    @Override
    public @Nullable FileSystemDirectoryListener createChainedDirectoryListener(Module module)
    {
        return null;
    }
}
