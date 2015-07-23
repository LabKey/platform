/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

import java.lang.ref.WeakReference;

/**
 * User: adam
 * Date: 1/15/12
 * Time: 10:23 PM
 *
 * Thread safety need to be ensured by the caller
 */
public class Wrapper<V>
{
    @SuppressWarnings({"unchecked"})
    protected V value = (V) BlockingCache.UNINITIALIZED;
    // weak reference, because I'm paranoid of accidentally holding onto threads
    protected WeakReference<Thread> loadingThread;

    Object getLockObject()
    {
        return this;
    }

    void setLoading()
    {
        assert Thread.holdsLock(getLockObject());
        loadingThread = new WeakReference<>(Thread.currentThread());
    }

    // call in finally
    void doneLoading()
    {
        assert Thread.holdsLock(getLockObject());
        loadingThread = null;
    }

    void loadFailed()
    {
        assert Thread.holdsLock(this);
        value =  (V) BlockingCache.UNINITIALIZED;
        doneLoading();
    }

    boolean isLoading()
    {
        assert Thread.holdsLock(getLockObject());
        Thread t = null==loadingThread ? null : loadingThread.get();
        if (null == t)
            return false;
        if (t == Thread.currentThread())
            throw new IllegalStateException("Caller is already loading this object!");
        return true;
    }

    void setValue(V v)
    {
        assert Thread.holdsLock(getLockObject());
        value = v;
        doneLoading();
    }

    public V getValue()
    {
        assert Thread.holdsLock(getLockObject());
        return value == BlockingCache.UNINITIALIZED ? null : value;
    }
}
