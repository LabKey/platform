package org.labkey.api.action;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* User: adam
* Date: Jun 27, 2010
* Time: 12:17:25 AM
*/
public class StatusAppender extends AppenderSkeleton
{
    private final List<String> _status = Collections.synchronizedList(new ArrayList<String>());

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

        // Synchronize since we're iterating the sublist and to ensure size() is consistent
        synchronized (_status)
        {
            if (null != offset && offset.intValue() > 0)
                firstIndex = Math.min(_status.size(), offset.intValue());

            return new LinkedList<String>(_status.subList(firstIndex, _status.size()));
        }
    }
}
