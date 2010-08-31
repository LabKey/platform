package org.labkey.api.collections;

import junit.framework.TestCase;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * User: adam
 * Date: Aug 30, 2010
 * Time: 3:28:25 PM
 */
public class CaseInsensitiveMapTestCase extends TestCase
{
    @Test
    public void multiThreadStressTest()
    {
        final int count = 1000;
        final int threads = 2;
        final String key = "ThisIsATest";
        final Object value = new Object();

        Map<String, Object> map = new CaseInsensitiveHashMap<Object>();
        map.put(key, value);

        Random random = new Random();
        Set<String> keys = new LinkedHashSet<String>();

        // Create an ordered set containing <count> keys with unique random casings of <key> 
        while (keys.size() < count)
        {
            StringBuilder candidate = new StringBuilder(key.length());

            for (int i = 0; i < key.length(); i++)
            {
                if (random.nextBoolean())
                    candidate.append(Character.toLowerCase(key.charAt(i)));
                else
                    candidate.append(Character.toUpperCase(key.charAt(i)));
            }

            String s = candidate.toString();

            if (!keys.contains(s))
                keys.add(s);
        }

        keys = keys;
    }
}
