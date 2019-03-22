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
 * NOTE: If the consumer/producer need to communicate errors, they may need to use interrupt() so that
 * they do not hang in wait().
 */
public class SwapQueue<T>
{
    T _full = null;
    T _empty = null;
    boolean _done = false;


    public SwapQueue()
    {
    }


    /**
     * There still may be work to do when close() is called, e.g. _full != null,
     * however no more work will be submitted
     */
    public synchronized void close()
    {
        _done = true;
        notifyAll();
    }


    public synchronized boolean isClosed()
    {
        return _done;
    }


    @Nullable
    public synchronized T swapEmptyForFull(@NotNull T empty) throws InterruptedException
    {
        putEmpty(empty);
        return getFull();
    }


    @Nullable
    public synchronized T swapFullForEmpty(@NotNull T full) throws InterruptedException
    {
        putFull(full);
        return getEmpty();
    }


    /** May return null if the queue is closed() */
    @Nullable
    public synchronized T getFull() throws InterruptedException
    {
        while (null == _full)
        {
            if (_done)
                return null;
            wait();
        }
        T ret = _full;
        _full = null;
        return ret;
    }


    public synchronized void putFull(T full) throws InterruptedException
    {
        if (_done)
            throw new IllegalStateException("Shouldn't be adding to a closed queue.");
        while (null != _full)
            wait();
        _full = full;
        notifyAll();
    }


    /** May return null if the queue is closed() */
    @Nullable
    public synchronized T getEmpty() throws InterruptedException
    {
        while (null == _empty)
        {
            if (_done)
                return null;
            wait();
        }
        T ret = _empty;
        _empty = null;
        return ret;
    }


    public synchronized void putEmpty(T empty) throws InterruptedException
    {
        if (_done)
            return;
        while (null != _empty)
        {
            wait();
        }
        _empty = empty;
        notifyAll();
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testTwoItems() throws Exception
        {
            final Throwable[] unexpectedException = new Throwable[] {null};
            final AtomicInteger item1 = new AtomicInteger();
            final AtomicInteger item2 = new AtomicInteger();
            final AtomicInteger counter = new AtomicInteger();
            final SwapQueue<AtomicInteger> _queue = new SwapQueue<>();

            Runnable filler = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            AtomicInteger item = item1;
                            for (int i=0 ; i<1000 ; i++)
                            {
                                counter.incrementAndGet();
                                item.incrementAndGet();
                                item = _queue.swapFullForEmpty(item);
                                assert null != item;
                            }
                            _queue.close();
                        }
                        catch (Throwable x)
                        {
                            unexpectedException[0] = x;
                        }
                    }
                };

            Runnable returner = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            AtomicInteger item = item2;
                            while (null != (item = _queue.swapEmptyForFull(item)))
                            {
                                counter.incrementAndGet();
                                item.decrementAndGet();
                            }
                        }
                        catch (Throwable x)
                        {
                            unexpectedException[0] = x;
                        }
                    }
                };

            Thread t1 = new Thread(filler);
            Thread t2 = new Thread(returner);
            t1.start();
            t2.start();
            t1.join(60*1000);
            t2.join(60*1000);
            assertNull(unexpectedException[0]);
            assertEquals(0, item1.get());
            assertEquals(0, item2.get());
            assertEquals(2000, counter.get());
        }

        @Test
        public void testOneItem() throws Exception
        {
            final Throwable[] unexpectedException = new Throwable[] {null};
            final AtomicInteger item1 = new AtomicInteger();
            final AtomicInteger counter = new AtomicInteger();
            final SwapQueue<AtomicInteger> _queue = new SwapQueue<>();
            _queue.putEmpty(item1);

            Runnable filler = new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        AtomicInteger item;
                        for (int i = 0; i < 1000; i++)
                        {
                            counter.incrementAndGet();
                            item = _queue.getEmpty();
                            assert null != item;
                            item.incrementAndGet();
                            _queue.putFull(item);
                        }
                        _queue.close();
                    }
                    catch (Throwable x)
                    {
                        unexpectedException[0] = x;
                    }
                }
            };

            Runnable returner = new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        AtomicInteger item;
                        while (null != (item = _queue.getFull()))
                        {
                            counter.incrementAndGet();
                            item.decrementAndGet();
                            _queue.putEmpty(item);
                        }
                        assert _queue.isClosed();
                    }
                    catch (Throwable x)
                    {
                        unexpectedException[0] = x;
                    }
                }
            };

            Thread t1 = new Thread(filler);
            Thread t2 = new Thread(returner);
            t1.start();
            t2.start();
            t1.join(60 * 1000);
            t2.join(60 * 1000);
            assertNull(unexpectedException[0]);
            assertEquals(0, item1.get());
            assertEquals(2000, counter.get());
        }


        @Test
        public void testInterrupt() throws Exception
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
                    try
                    {
                        AtomicInteger item = item1;
                        for (int i=0 ; i<500 ; i++)
                        {
                            counter.incrementAndGet();
                            item.incrementAndGet();
                            item = _queue.swapFullForEmpty(item);
                            assert null != item;
                        }
                        _queue.close();
                    }
                    catch (InterruptedException x)
                    {
                                /* */
                    }
                }
            };
            final Thread t1 = new Thread(filler);

            Runnable returner = new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        AtomicInteger item = item2;
                        for (int i = 0; i < 500; i++)
                        {
                            item = _queue.swapEmptyForFull(item);
                            counter.incrementAndGet();
                            item.decrementAndGet();
                        }
                        t1.interrupt();
                    }
                    catch (InterruptedException x)
                    {
                                /* */
                    }
                }
            };

            final Thread t2 = new Thread(returner);

            t1.start();
            t2.start();
            t1.join(60 * 1000);
            t2.join(60 * 1000);
            assertEquals(0, item1.get());
            assertEquals(0, item2.get());
            assertEquals(1000, counter.get());
        }

    }
}
