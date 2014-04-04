/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.concurrent;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by matthew on 3/13/14.
 *
 * Wrapper for java.util.concurrent.Semaphore that returns a Closeable
 */
public class CountingSemaphore
{
    final Semaphore s;

    public CountingSemaphore(int permits, boolean fair)
    {
        s = new Semaphore(permits, fair);
    }


    public AutoCloseable tryAcquire(long timeout) throws InterruptedException
    {
        boolean b = s.tryAcquire(timeout, TimeUnit.MILLISECONDS);
        return b ? new _Permit() : null;
    }


    public AutoCloseable acquire() throws InterruptedException
    {
        s.acquire();
        return new _Permit();
    }

    private class _Permit implements AutoCloseable
    {
        @Override
        public void close() throws Exception
        {
            s.release();
        }
    }
}
