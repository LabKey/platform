/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.api.collections;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: matthew
 * Date: 5/8/13
 * Time: 12:31 PM
 *
 * Very simple synchronized for swapping two items between a foreground and background thread.
 * Be careful to get your starting conditions right, and this will work great.
 *
 * This is basically like two BlockingQueues with a max size of one element.  One queue for 'full elements
 * (elements to be consumed), and one queue for empty elements (consumed).
 *
 */
public class SwapQueue<T>
{
    T _full = null;
    T _empty = null;
    boolean _done = false;


    public SwapQueue()
    {
    }


    public synchronized void close()
    {
        _done = true;
        notifyAll();
    }


    @Nullable
    public synchronized T swapEmptyForFull(@NotNull T empty)
    {
        putEmpty(empty);
        return getFull();
    }


    @NotNull
    public synchronized T swapFullForEmpty(@NotNull T full)
    {
        putFull(full);
        return getEmpty();
    }


    public synchronized T getFull()
    {
        while (null == _full)
        {
            if (_done)
                return null;
            try {wait();}catch(InterruptedException x){}
        }
        T ret = _full;
        _full = null;
        return ret;
    }


    public synchronized void putFull(T full)
    {
        if (_done)
            throw new IllegalStateException("Shouldn't be adding to a closed queue.");
        while (null != _full)
        {
            try {wait();}catch(InterruptedException x){}
        }
        _full = full;
        notifyAll();
    }


    public synchronized T getEmpty()
    {
        while (null == _empty)
        {
            try {wait();}catch(InterruptedException x){}
        }
        T ret = _empty;
        _empty = null;
        return ret;
    }


    public synchronized void putEmpty(T empty)
    {
        while (null != _empty)
        {
            try {wait();}catch(InterruptedException x){}
        }
        _empty = empty;
        notifyAll();
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testTwoItems() throws Exception
        {
            final AtomicInteger item1 = new AtomicInteger();
            final AtomicInteger item2 = new AtomicInteger();
            final AtomicInteger counter = new AtomicInteger();
            final SwapQueue<AtomicInteger> _queue = new SwapQueue<>();

            Runnable filler = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        AtomicInteger item = item1;
                        for (int i=0 ; i<1000 ; i++)
                        {
                            counter.incrementAndGet();
                            item.incrementAndGet();
                            item = _queue.swapFullForEmpty(item);
                        }
                        _queue.close();
                    }
                };

            Runnable returner = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        AtomicInteger item = item2;
                        while (null != (item = _queue.swapEmptyForFull(item)))
                        {
                            counter.incrementAndGet();
                            item.decrementAndGet();
                        }
                    }
                };

            Thread t1 = new Thread(filler);
            Thread t2 = new Thread(returner);
            t1.start();
            t2.start();
            t1.join(60*1000);
            t2.join(60*1000);
            assertEquals(0, item1.get());
            assertEquals(0, item2.get());
            assertEquals(2000, counter.get());
        }

        @Test
        public void testOneItem() throws Exception
        {
            final AtomicInteger item1 = new AtomicInteger();
            final AtomicInteger counter = new AtomicInteger();
            final SwapQueue<AtomicInteger> _queue = new SwapQueue<>();
            _queue.putEmpty(item1);

            Runnable filler = new Runnable()
            {
                @Override
                public void run()
                {
                    AtomicInteger item;
                    for (int i=0 ; i<1000 ; i++)
                    {
                        counter.incrementAndGet();
                        item = _queue.getEmpty();
                        item.incrementAndGet();
                        _queue.putFull(item);
                    }
                    _queue.close();
                }
            };

            Runnable returner = new Runnable()
            {
                @Override
                public void run()
                {
                    AtomicInteger item;
                    while (null != (item = _queue.getFull()))
                    {
                        counter.incrementAndGet();
                        item.decrementAndGet();
                        _queue.putEmpty(item);
                    }
                }
            };

            Thread t1 = new Thread(filler);
            Thread t2 = new Thread(returner);
            t1.start();
            t2.start();
            t1.join(60*1000);
            t2.join(60*1000);
            assertEquals(0, item1.get());
            assertEquals(2000, counter.get());
        }
    }
}
