/*
 * Copyright (c) 2017 LabKey Corporation
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
