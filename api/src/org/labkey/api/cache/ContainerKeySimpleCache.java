package org.labkey.api.cache;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.Set;

public class ContainerKeySimpleCache<V> extends SimpleKeyMappingCache<Container, GUID, V>
{
    public ContainerKeySimpleCache(@NotNull SimpleCache<GUID, V> delegate)
    {
        // Some caches use a null key for cross-container data (e.g., ExternalSchemaDefCache)
        super(delegate, c -> null == c ? null : c.getEntityId(), ContainerManager::getForId);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testDeletedContainer()
        {
            User user = TestContext.get().getUser();
            Container parent = JunitUtil.getTestContainer();
            Container child = ContainerManager.ensureContainer(parent, "ContainerKeySimpleCache", user);

            try (Cache<Container, String> cache = CacheManager.getTemporaryCache(Container.class, 10, CacheManager.UNLIMITED, "ContainerKeySimpleCache test", null))
            {
                cache.put(parent, "parent");
                cache.put(child, "child");
                assertEquals(Set.of(parent, child), cache.getKeys());

                assertTrue(ContainerManager.delete(child, user));

                // A plain Container-keyed cache would still report the deleted child here
                assertEquals(Set.of(parent), cache.getKeys());
                assertEquals(1, cache.removeUsingFilter(_ -> true));
                assertNull(cache.get(parent));

                // The deleted Container object still has its GUID, so direct access works
                assertEquals("child", cache.get(child));
                cache.remove(child);
                assertEquals(0, cache.getTrackingCache().size());
            }
            finally
            {
                if (null != ContainerManager.getForId(child.getEntityId()))
                    ContainerManager.delete(child, user);
            }
        }
    }
}
