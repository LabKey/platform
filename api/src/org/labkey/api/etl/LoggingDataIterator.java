package org.labkey.api.etl;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;
import java.util.Formatter;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-05-27
 * Time: 1:10 PM
 */
public class LoggingDataIterator extends AbstractDataIterator
{
    static Logger _staticLog = Logger.getLogger(LoggingDataIterator.class);
    Logger _log = _staticLog;
    Level _pri = Level.DEBUG;

    DataIterator _data;


    public static DataIterator wrap(DataIterator in)
    {
        if (_staticLog.isEnabledFor(Priority.DEBUG))
            return new LoggingDataIterator(in);
        return in;
    }


    public LoggingDataIterator(DataIterator in)
    {
        super(null);
        _data = in;
    }

    public LoggingDataIterator(DataIterator in, Logger log)
    {
        super(null);
        _data = in;
        _log = log;
    }

    @Override
    public int getColumnCount()
    {
        return _data.getColumnCount();
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _data.getColumnInfo(i);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        boolean hasNext = _data.next();
        if (!hasNext)
            return false;

        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb);

        String debugName = _data.getClass().toString();
        if (_data instanceof AbstractDataIterator)
            debugName += ": " + ((AbstractDataIterator)_data)._debugName;
        sb.append(debugName).append("\n");

        for (int i=0 ; i<=_data.getColumnCount() ; i++)
        {
            String name = _data.getColumnInfo(i).getName();
            if (name.length() > 50)
                name = name.substring(name.length()-50);
            Object value = _data.get(i);
            if (null == value)
                value = "";
            formatter.format("%50s  %s\n", name, value);
        }

        _log.log(_pri, sb.toString());

        return true;
    }

    @Override
    public Object get(int i)
    {
        return _data.get(i);
    }

    @Override
    public void close() throws IOException
    {
        _data.close();
    }
}
