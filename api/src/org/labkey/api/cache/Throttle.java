package org.labkey.api.cache;

import java.util.function.Consumer;

/**
 * Created by adam on 4/26/2017.
 *
 * <p>General-purpose throttle that guarantees code executes at most once per specified time period per key. For example,
 * to log a warning about specific Users at most once per hour, initialize the throttle like this:</p>
 *
 * <pre>
 * {@code
 * Throttle<User> THROTTLE = new Throttle<>("bad users", 100, CacheManager.HOUR, user -> LOG.warn(user + " is a bad hombre!"));}
 * </pre>
 *
 * <p>And then attempt the logging like this:</p>
 *
 * <pre>
 * {@code
 * if (isBad(user))
 *        THROTTLE.execute(user);
 * }
 * </pre>
 */
public class Throttle<K>
{
    private final BlockingCache<K, K> _cache;

    public Throttle(String name, int limit, long timeToLive, Consumer<K> consumer)
    {
        _cache = CacheManager.getBlockingCache(limit, timeToLive, "Throttle for " + name, (key, argument) ->
        {
            consumer.accept(key);
            return key;
        });
    }

    public void execute(K key)
    {
        _cache.get(key);
    }
}
