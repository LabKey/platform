/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

import org.labkey.api.cache.DbCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.sql.SQLException;

/**
 * User: klum
 * Date: Jan 29, 2010
 * Time: 12:53:12 PM
 */
public class FileRootManager
{
    public static final String FILECONTENT_SCHEMA_NAME = "filecontent";
    private static final FileRootManager _instance = new FileRootManager();
    private static final FileRoot NULL_ROOT = new FileRoot();

    private FileRootManager(){}

    public static FileRootManager get()
    {
        return _instance;
    }

    public static DbSchema getFileContentSchema()
    {
        return DbSchema.get(FILECONTENT_SCHEMA_NAME);
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
        
        try
        {
            String cacheKey = getCacheKey(c);
            FileRoot root = (FileRoot) DbCache.get(getTinfoFileRoots(), cacheKey);

            if (root != null)
                return (root == NULL_ROOT) ? new FileRoot(c) : root;

            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("Container"), c.getId());
            FileRoot[] roots = Table.select(getTinfoFileRoots(), Table.ALL_COLUMNS, filter, null, FileRoot.class);
            if (roots.length > 0)
            {
                root = roots[0];
                DbCache.put(getTinfoFileRoots(), cacheKey, root);
            }
            else
            {
                DbCache.put(getTinfoFileRoots(), cacheKey, NULL_ROOT);
                root = new FileRoot(c);
            }
            return root;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public void deleteFileRoot(Container c)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("Container", c.getId());
            Table.delete(getTinfoFileRoots(), filter);

            DbCache.remove(getTinfoFileRoots(), getCacheKey(c));
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public FileRoot saveFileRoot(User user, FileRoot root)
    {
        try
        {
            if (root.isNew())
                return Table.insert(user, getTinfoFileRoots(), root);
            else
            {
                DbCache.remove(getTinfoFileRoots(), root.getContainer());
                return Table.update(user, getTinfoFileRoots(), root, root.getRowId());
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public void clearCache()
    {
        DbCache.clear(getTinfoFileRoots());
    }
}
