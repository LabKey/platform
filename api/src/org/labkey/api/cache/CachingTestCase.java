package org.labkey.api.cache;

import org.junit.Assert;
import org.junit.Test;

public class CachingTestCase extends Assert
{
    @Test
    public void testCaching()
    {
        DbCache.logUnmatched();
    }
}
