/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.dataiterator;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * User: matthew
 * Date: 5/4/13
 * Time: 9:21 AM
 */
public class AsyncDataIterator implements DataIterator
{
    public static class Builder implements DataIteratorBuilder
    {
        final DataIteratorBuilder _in;
        public Builder(DataIteratorBuilder in)
        {
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator it = _in.getDataIterator(context);
            if (null == it)
                return null;
            return new AsyncDataIterator(it,context);
        }
    }

    // DataIterator instances are not usually thread safe, so use a lock for pass through calls
    Object _itLock;
    DataIterator _it;
    int _columnCount;
    DataIteratorContext _context;

    Thread _reader;

//    ArrayListMap.FindMap<String> _findMap;
//    static ArrayListMap<String,Object> eof = new ArrayListMap<String,Object>();
//    protected LinkedBlockingQueue<ArrayListMap<String,Object>> _queue;
    protected LinkedBlockingQueue<Object[]> _queue;
    static Object[] eof = new Object[0];

    ArrayList<Object[]> _buffer = new ArrayList<>(100);
    int _currentRow = -1;

    AtomicInteger _partnerCount = new AtomicInteger(1);


    public AsyncDataIterator(DataIterator it, DataIteratorContext context)
    {
        this(it,context,true);
    }


    public AsyncDataIterator(DataIterator it, DataIteratorContext context, boolean autostart)
    {
        _queue = new LinkedBlockingQueue<>(1000);
        _itLock = new Object();
        _it = it;
        _columnCount = it.getColumnCount();
        _context = context;
//        _findMap = new ArrayListMap.FindMap<String>(new CaseInsensitiveHashMap<Integer>());
//        for (int i=0 ; i<= it.getColumnCount() ; i++)
//            _findMap.put(it.getColumnInfo(i).getName(), i);
        if (autostart)
            start();
    }



    protected AsyncDataIterator(AsyncDataIterator partner)
    {
        synchronized (partner._itLock)
        {
            this._itLock = partner._itLock;
            this._it = partner._it;
//            this._findMap = partner._findMap;
            this._queue = partner._queue;
            _partnerCount = partner._partnerCount;
            int count = _partnerCount.incrementAndGet();
            assert count > 1;
        }
    }


    public AsyncDataIterator fork()
    {
        return new AsyncDataIterator(this);
    }



    private void start()
    {
        _reader = new Thread(new ReadRunnable(), "AsyncDataIterator");
        _reader.start();
    }


    private class ReadRunnable implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                while (!Thread.interrupted() && _it.next())
                {
                    Object[] row = new Object[_columnCount+1];
                    for (int i=0 ; i<=_columnCount ; i++)
                        row[i] = _it.get(i);
                    _queue.put(row);
                }
            }
            catch (BatchValidationException x)
            {
                assert x.hasErrors();
            }
            catch (InterruptedException x)
            {
            }
            try { _queue.put(eof); } catch (InterruptedException x) { }
            // don't close _it from background thread, this causes DbScope/Transaction problems
            // try {_it.close(); } catch (IOException x) {}
        }
    }


    @Override
    public int getColumnCount()
    {
        synchronized (_itLock)
        {
            return _it.getColumnCount();
        }
    }


    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        synchronized (_itLock)
        {
            return _it.getColumnInfo(i);
        }
    }


    @Override
    public boolean next() throws BatchValidationException
    {
        Object[] row=null;
        if (_currentRow+1 >= _buffer.size())
        {
            _buffer.clear();
            _currentRow = -1;
            while (_buffer.isEmpty())
            {
                int count = _queue.drainTo(_buffer, 100);
                if (count == 0)
                {
                    try { row = _queue.take(); } catch (InterruptedException x) { }
                    if (null != row)
                        _buffer.add(row);
                    if (eof == row)
                        break;
                }
            }
        }
        _currentRow++;
        row = _buffer.get(_currentRow);
        if (eof == row)
        {
            try { _queue.put(eof); } catch (InterruptedException x) { }
            return false;
        }
        return true;
    }


    @Override
    public String getDebugName()
    {
        return this.getClass().getName();
    }


    @Override
    public boolean isConstant(int i)
    {
        synchronized (_itLock)
        {
            return _it.isConstant(i);
        }
    }


    @Override
    public Object getConstantValue(int i)
    {
        synchronized (_itLock)
        {
            return _it.getConstantValue(i);
        }
    }


    @Override
    public Object get(int i)
    {
        if (i == 0)
            return _currentRow+1;
        return _buffer.get(_currentRow)[i];
    }


    @Override
    public void close() throws IOException
    {
        synchronized (_itLock)
        {
            if (0 == _partnerCount.decrementAndGet())
            {
                _reader.interrupt();
                try {_reader.join();} catch (InterruptedException x) {}
                _it.close();
            }
        }
    }

    @Override
    public void debugLogInfo(StringBuilder sb)
    {
        sb.append(this.getClass().getName() + "\n");
        if (null != _it)
            _it.debugLogInfo(sb);
    }

}
