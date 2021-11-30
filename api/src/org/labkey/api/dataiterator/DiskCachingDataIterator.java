package org.labkey.api.dataiterator;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.fasterxml.jackson.databind.util.ByteBufferBackedOutputStream;
import org.apache.commons.collections.map.AbstractReferenceMap;
import org.apache.commons.collections.map.ReferenceMap;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.arrays.IntegerArray;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.util.GUID;
import org.labkey.api.util.UnexpectedException;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Map;

public class DiskCachingDataIterator extends CachingDataIterator
{
    final int limit;         // max _data.size()
    final int batchSize;     // number or rows to read/write at a time <= limit

    int _diskMarkPosition;

    DiskCachingDataIterator(DataIterator in)
    {
        super(in);
        limit = 50_000;
        batchSize = 1000;
    }

    /* for testing */
    DiskCachingDataIterator(DataIterator in, int limit, int batch)
    {
        super(in);
        this.limit = limit;
        batchSize = Math.min(batch,limit);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        return super.next();
    }

    @Override
    protected void populateRows() throws BatchValidationException
    {
        if (_data.size() >= limit)
            writeToDisk(); // maybe write rows to disk

        // if our position matches the input position, just advance as normal (_currentPosition is already incremented)
        if (_currentPosition == _inputPosition+1)
            super.populateRows();
        else
            loadFromDisk(_currentPosition);
    }

    @Override
    protected void mark()
    {
        _diskMarkPosition = _currentPosition+1;
    }

    @Override
    public void beforeFirst()
    {
        _diskMarkPosition = 0;
        reset();
    }

    @Override
    protected void reset()
    {
        _currentPosition = _diskMarkPosition-1;
        if (_currentPosition < _markPosition-1 || _currentPosition >= _markPosition+_data.size())
        {
            _markPosition = _diskMarkPosition;
            _data.clear();
        }
    }

    File _tempFile;
    RandomAccessFile _randomAccessFile;
    MappedByteBuffer _buffer;
    ObjectOutputStream _objectOutputStream;

    int _bufferEndPosition=0;     // I don't think the buffer will tell where the end of the data is???
    IntegerArray _index = new IntegerArray();
    Map<Integer,Object[]> _map = (Map<Integer,Object[]>)new ReferenceMap(AbstractReferenceMap.HARD, AbstractReferenceMap.SOFT);
    int firstRowOnDisk = -1;    // index of first row written to disk
    int lastRowOnDisk = -1;     // index of last row written (number or rows = lastRowOnDisk-firstRowOnDisk+1)

    private void writeToDisk()
    {
        if (null == _buffer)
        {
            try
            {
                _tempFile = File.createTempFile("buffer", "dat");
                _tempFile.deleteOnExit();
                _randomAccessFile = new RandomAccessFile(_tempFile, "rw");
                _buffer = _randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 128 * 1024);
            }
                catch (IOException x)
            {
                throw UnexpectedException.wrap(x);
            }
        }
        int copyCount = Math.min(batchSize, _data.size());
        for (int i=0 ; i<copyCount ; i++)
        {
            writeRowToDisk(_markPosition + i, _data.get(i));
        }
        _data.removeRange(0,copyCount);
        _markPosition += copyCount;
    }

    void writeRowToDisk(int pos, Object[] row)
    {
        _map.put(pos, row);
        if (pos == lastRowOnDisk+1)
        {
            try
            {
                _index.add(_bufferEndPosition);
                while (true)
                {
                    try
                    {
                        _buffer.position(_bufferEndPosition);
                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(new ByteBufferBackedOutputStream(_buffer));
                        objectOutputStream.writeObject(row);
                        objectOutputStream.close();
                        break;
                    }
                    catch (BufferOverflowException x)
                    {
                        int capacity = _buffer.capacity();
                        if (capacity < 1025*1024)
                            capacity += capacity < 1024*1024 ? 128 * 1024 : 1024*1024;
                        _buffer = _randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, capacity);
                    }
                }
                _bufferEndPosition = _buffer.position();
                lastRowOnDisk = pos;
            }
            catch (IOException x)
            {
                throw UnexpectedException.wrap(x);
            }
        }
        if (pos > lastRowOnDisk+1 || pos < firstRowOnDisk)
            throw new IllegalStateException();
    }

    private void loadFromDisk(int start)
    {
        int end = start + batchSize;
        boolean assertEnabled = false;
        assert true==(assertEnabled=true);
        for (int row=start ; row<end ; row++)
        {
            try
            {
                Object[] data = _map.get(row);
                if (null == data || assertEnabled)
                {
                    int pos = _index.get(row);
                    _buffer.position(pos);
                    ObjectInputStream objectInputStream = new ObjectInputStream(new ByteBufferBackedInputStream(_buffer));
                    Object[] dataDisk = (Object[])objectInputStream.readObject();
                    objectInputStream.close();
                    assert null==data || Arrays.equals(data,dataDisk);
                    data = dataDisk;
                }
                _data.add(data);
            }
            catch (IOException|ClassNotFoundException x)
            {
                throw UnexpectedException.wrap(x);
            }
        }
        //return (_cu) < _data.size();
    }

    @Override
    public void close() throws IOException
    {
        //_map.clear();
        IOUtils.closeQuietly(_objectOutputStream);
        if (null != _randomAccessFile)
            _randomAccessFile.close();
        if (null != _tempFile && _tempFile.isFile())
            _tempFile.delete();
    }

    private static String[] as(String... arr)
    {
        return arr;
    }

    public static class DiskTestCase extends Assert
    {
        StringTestIterator simpleData = new StringTestIterator
        (
                Arrays.asList("IntNotNull", "Text", "EntityId", "Int"),
                Arrays.asList(
                        as("1", "one", GUID.makeGUID(), ""),
                        as("2", "two", GUID.makeGUID(), "/N"),
                        as("3", "three", GUID.makeGUID(), "3"),
                        as("4", "", GUID.makeGUID(), ""),
                        as("5", "", GUID.makeGUID(), ""),
                        as("6", "", GUID.makeGUID(), ""),
                        as("7", "", GUID.makeGUID(), ""),
                        as("8", "", GUID.makeGUID(), ""),
                        as("9", "", GUID.makeGUID(), ""),
                        as("10", "", GUID.makeGUID(), "")
                )
        );

        @Test
        public void scrollTest() throws Exception
        {
            simpleData.setScrollable(false);
            CachingDataIterator scrollable = new DiskCachingDataIterator(simpleData,3,2);

            scrollable.next();
            scrollable.next();
            scrollable.next();
            scrollable.next();
            scrollable.beforeFirst();
            scrollable.next();
            assertEquals("1",scrollable.get(1));
            scrollable.next();
            scrollable.next();
            scrollable.next();
            scrollable.next();
            scrollable.next();
            scrollable.next();
            scrollable.next();
            scrollable.beforeFirst();
            scrollable.next();
            assertEquals("1",scrollable.get(1));
            scrollable.mark();
            scrollable.next();
            assertEquals("2",scrollable.get(1));
            scrollable.next();
            scrollable.next();
            scrollable.next();
            scrollable.next();
            scrollable.next();
            scrollable.next();
            scrollable.reset();
            scrollable.next();
            assertEquals("2",scrollable.get(1));
            scrollable.close();
        }
    }
}
