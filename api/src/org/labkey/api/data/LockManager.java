/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.data;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages java.util.concurrent.Lock objects correlated with other objects to ensure. Will hand out the same Lock
 * for equivalent (in terms of .equals() and .hashCode()) objects as long as the object(s) are alive at the same time.
 * If the objects have been garbage collected, future Lock requests may return a new Lock.
 *
 * This is useful in conjunction with DbScope.ensureTransaction() to ensure that only a single thread is permitted
 * in the critical section, even if there are multiple equivalent instances of the object (preventing use of a simple
 * synchronized block, for example).

 *
 * Created by: jeckels
 * Date: 1/4/15
 */
public class LockManager<T>
{
    private final Map<T, Lock> _locks = new WeakHashMap<>();

    public synchronized Lock getLock(T instance)
    {
        Lock lock = _locks.get(instance);
        if (lock == null)
        {
            lock = new ReentrantLock();
        }
        // Always make sure that the latest instance that was passed in, which we know is still live and
        // not about to be garbage collected, is the key in the map. This ensures that we consistently use the
        // same Lock for a given object even if the instances are cycled via garbage collection. If a object
        // is completely garbage collected, it's fine to lose the Lock, as it means that no other threads are using
        // it anymore.
        _locks.put(instance, lock);
        return lock;
    }
}
