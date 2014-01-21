/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.study.StudyCachable;
import org.labkey.api.util.MemTracker;

/**
 * User: brittp
 * Date: Feb 10, 2006
 * Time: 2:33:38 PM
 */
public abstract class AbstractStudyCachable<T> implements StudyCachable<T>, Cloneable
{
    private boolean _mutable = true;

    public AbstractStudyCachable()
    {
        MemTracker.getInstance().put(this);
    }

    protected void verifyMutability()
    {
        if (!_mutable)
            throw new IllegalStateException("Cached objects are immutable; createMutable must be called first.");
    }

    public boolean isMutable()
    {
        return _mutable;
    }

    protected void unlock()
    {
        _mutable = true;
    }

    public void lock()
    {
        _mutable = false;
    }


    public T createMutable()
    {
        if (_mutable)
            return (T)this;
        try
        {
            T obj = (T) clone();
            ((AbstractStudyCachable) obj).unlock();
            return obj;
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public abstract void setContainer(Container container);
}
