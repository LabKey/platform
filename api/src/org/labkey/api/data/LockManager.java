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

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages java.util.concurrent.ReentrantLock objects correlated with other objects. Will hand out the same Lock
 * for equivalent (in terms of hashCode() values). Uses striping to manage the set of locks, balancing the total
 * number of Lock instances with avoiding contention.
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
    private final List<ReentrantLock> _locks;

    public LockManager()
    {
        this(23);
    }

    /** @param lockCount the number of locks to be used for striping */
    public LockManager(int lockCount)
    {
        _locks = new ArrayList<>(lockCount);
        for (int i = 0; i < lockCount; i++)
        {
            _locks.add(new ReentrantLock());
        }
    }

    public ReentrantLock getLock(@NotNull T instance)
    {
        // Choose the lock to use based on the object's hashCode()
        int lockIndex = (0x7fff & instance.hashCode()) % _locks.size();
        return _locks.get(lockIndex);
    }
}
