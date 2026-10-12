/*
 * Copyright (c) 2010-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.filecontent;

import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileRoot;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FileRootManager
{
    public static final String FILE_CONTENT_SCHEMA_NAME = "filecontent";

    private static final FileRootManager _instance = new FileRootManager();
    // The table is sparse, so cache every row in one entry (container id -> root) rather than a hit or miss per container
    private static final String CACHE_KEY = "AllRoots";
    private static final BlockingCache<String, Map<String, FileRoot>> CACHE = CacheManager.getBlockingCache(String.class, 1, CacheManager.DAY, "FileRoots", (key, argument) -> {
        Map<String, FileRoot> roots = new HashMap<>();
        new TableSelector(getTinfoFileRoots()).forEach(FileRoot.class, root -> roots.put(root.getContainer(), root));
        return Collections.unmodifiableMap(roots);
    });

    private FileRootManager(){}

    public static FileRootManager get()
    {
        return _instance;
    }

    public static DbSchema getFileContentSchema()
    {
        return DbSchema.get(FILE_CONTENT_SCHEMA_NAME, DbSchemaType.Module);
    }

    public static TableInfo getTinfoFileRoots()
    {
        return getFileContentSchema().getTable("FileRoots");
    }

    public FileRoot getFileRoot(Container c)
    {
        if (c == null)
            throw new IllegalArgumentException("getFileRoot: Container cannot be null");

        FileRoot root = CACHE.get(CACHE_KEY).get(c.getId());

        return null == root ? new FileRoot(c) : root;
    }

    public void deleteFileRoot(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        Table.delete(getTinfoFileRoots(), filter);

        clearCache();
    }

    public void saveFileRoot(User user, FileRoot root)
    {
        try
        {
            if (root.isNew())
            {
                Table.insert(user, getTinfoFileRoots(), root);
            }
            else
            {
                Table.update(user, getTinfoFileRoots(), root, root.getRowId());
            }
        }
        finally
        {
            clearCache();
        }
    }

    public void clearCache()
    {
        CACHE.remove(CACHE_KEY);
    }
}
