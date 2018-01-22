/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileRoot;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

/**
 * User: klum
 * Date: Jan 29, 2010
 * Time: 12:53:12 PM
 */
public class FileRootManager
{
    public static final String FILECONTENT_SCHEMA_NAME = "filecontent";
    private static final FileRootManager _instance = new FileRootManager();
    private static final BlockingStringKeyCache<FileRoot> CACHE = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "FileRoots", (key, c) -> {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("Container"), c);

        return new TableSelector(getTinfoFileRoots(), filter, null).getObject(FileRoot.class);
    });

    private FileRootManager(){}

    public static FileRootManager get()
    {
        return _instance;
    }

    public static DbSchema getFileContentSchema()
    {
        return DbSchema.get(FILECONTENT_SCHEMA_NAME, DbSchemaType.Module);
    }

    public static TableInfo getTinfoFileRoots()
    {
        return getFileContentSchema().getTable("FileRoots");
    }

    private String getCacheKey(Container c)
    {
        return c.getId();
    }

    public FileRoot getFileRoot(Container c)
    {
        if (c == null)
            throw new IllegalArgumentException("getFileRoot: Container cannot be null");
        
        FileRoot root = CACHE.get(getCacheKey(c), c);

        return null == root ? new FileRoot(c) : root;
    }

    public void deleteFileRoot(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        Table.delete(getTinfoFileRoots(), filter);

        CACHE.remove(getCacheKey(c));
    }

    public FileRoot saveFileRoot(User user, FileRoot root)
    {
        try
        {
            if (root.isNew())
            {
                return Table.insert(user, getTinfoFileRoots(), root);
            }
            else
            {
                return Table.update(user, getTinfoFileRoots(), root, root.getRowId());
            }
        }
        finally
        {
            // Must clear cache in both insert & update, otherwise NULL marker remains in the cache
            CACHE.remove(root.getContainer());
        }
    }

    public void clearCache()
    {
        CACHE.clear();
    }
}
