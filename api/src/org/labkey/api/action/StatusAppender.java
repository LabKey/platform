package org.labkey.api.action;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
* User: adam
* Date: Jun 27, 2010
* Time: 12:17:25 AM
*/
public class StatusAppender extends AppenderSkeleton
{
    private final List<String> _status = new CopyOnWriteArrayList<String>();

    @Override
    protected void append(LoggingEvent event)
    {
        _status.add(event.getMessage().toString());
    }

    @Override
    public void close()
    {
    }

    @Override
    public boolean requiresLayout()
    {
        return false;
    }

    public Collection<String> getStatus(@Nullable Integer offset)
    {
        int firstIndex = 0;

        if (null != offset && offset.intValue() > 0)
            firstIndex = Math.min(_status.size(), offset.intValue());

        return _status.subList(firstIndex, _status.size());
    }
}
