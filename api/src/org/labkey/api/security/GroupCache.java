/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.api.security;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;

/**
 * User: adam
 * Date: 10/15/11
 * Time: 10:09 AM
 */
public class GroupCache
{
    private static final CoreSchema CORE = CoreSchema.getInstance();
    private static final BlockingCache<Integer, Group> CACHE = CacheManager.getBlockingCache(10000, CacheManager.DAY, "Groups", new CacheLoader<Integer, Group>()
    {
        @Override
        public Group load(Integer groupId, Object argument)
        {
            SQLFragment sql = new SQLFragment("SELECT * FROM " + CORE.getTableInfoPrincipals() + " WHERE Type <> 'u' AND UserId = ?", groupId);
            SqlSelector selector = new SqlSelector(CORE.getSchema(), sql);

            return selector.getObject(Group.class);
        }
    });


    static @Nullable Group get(int groupId)
    {
        return CACHE.get(groupId);
    }


    static void uncache(int groupId)
    {
        CACHE.remove(groupId);
    }


    static void uncacheAll()
    {
        CACHE.clear();
    }
}
