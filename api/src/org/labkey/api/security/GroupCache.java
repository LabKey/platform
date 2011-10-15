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
    private static final BlockingCache<Integer, Group> CACHE = new BlockingCache<Integer, Group>(CacheManager.<Integer, Object>getCache(10000, CacheManager.DAY, "Groups"), new CacheLoader<Integer, Group>()
    {
        @Override
        public Group load(Integer groupId, Object argument)
        {
            SQLFragment sql = new SQLFragment("SELECT Name, UserId, Container, OwnerId FROM " + CORE.getTableInfoPrincipals() + " WHERE Type <> 'u' AND UserId = ?", groupId);
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
