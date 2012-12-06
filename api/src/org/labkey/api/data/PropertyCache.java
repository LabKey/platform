package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.Map;

/**
 * User: adam
 * Date: 11/30/12
 * Time: 10:46 PM
 */
public class PropertyCache
{
    private static final BlockingStringKeyCache<Map<String, String>> BLOCKING_CACHE = CacheManager.getBlockingStringKeyCache(10000, CacheManager.DAY, "Properties", new PropertyLoader());

    public static @Nullable Map<String, String> getProperties(User user, Container container, String category)
    {
        String key = getCacheKey(user, container, category);

        return BLOCKING_CACHE.get(key, new Object[]{user, container, category});
    }

    public static void remove(PropertyMap map)
    {
        Object[] params = map.getCacheParams();

        BLOCKING_CACHE.remove(getCacheKey((Integer)params[0], (String)params[1], (String)params[2]));
    }

    private static String getCacheKey(User user, Container container, String category)
    {
        return getCacheKey(user.getUserId(), container.getId(), category);
    }

    private static String getCacheKey(int userId, String containerId, String category)
    {
        return String.valueOf(userId) + "/" + String.valueOf(containerId) + "/" + category;
    }

    private static class PropertyLoader implements CacheLoader<String, Map<String, String>>
    {
        @Override
        public Map<String, String> load(String key, Object argument)
        {
            Object[] params = (Object[])argument;
            PropertyMap map = PropertyManager.getWritableProperties((User)params[0], (Container)params[1], (String)params[2], false);

            return null != map ? Collections.unmodifiableMap(map) : null;
        }
    }
}
