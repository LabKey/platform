/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.labkey.api.mbean.ErrorsMXBean;
import org.labkey.api.mbean.LabKeyManagement;

import javax.management.DynamicMBean;
import javax.management.StandardMBean;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * User: matthewb
 * Date: 2012-02-28
 * Time: 4:10 PM
 */
public class MXBeanAppender extends org.apache.log4j.AppenderSkeleton implements ErrorsMXBean
{
    private static class _Error implements Error
    {
        final LoggingEvent _event;
        _Error(LoggingEvent e)
        {
            _event = e;
        }

        @Override
        public Date getTime()
        {
            return new Date(_event.getTimeStamp());
        }

        @Override
        public String getMessage()
        {
            return String.valueOf(_event.getMessage());
        }

        @Override
        public String getThreadName()
        {
            return _event.getThreadName();
        }

        @Override
        public String getLevel()
        {
            return _event.getLevel().toString();
        }

        @Override
        public String getLoggerName()
        {
            return _event.getLoggerName();
        }
    }

    LinkedList<_Error> _events = new LinkedList<>();

    public MXBeanAppender()
    {
        DynamicMBean mbean = new StandardMBean(this, ErrorsMXBean.class, true);
        LabKeyManagement.register(mbean, "Errors", "recent");
    }

    @Override
    protected synchronized void append(LoggingEvent loggingEvent)
    {
        if (loggingEvent.getLevel().toInt() == Level.DEBUG.toInt())
            return;
        clean();
        _events.addFirst(new _Error(loggingEvent));
    }

    private synchronized void clean()
    {
        long yesterday = HeartBeat.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        while (_events.size() > 100 || !_events.isEmpty() && _events.getLast()._event.getTimeStamp() < yesterday)
            _events.removeLast();
    }


    @Override
    public synchronized void close()
    {
        _events.clear();
    }


    @Override
    public boolean requiresLayout()
    {
        return false;
    }


    /* ErrorsMXBean */

    @Override
    public synchronized Error[] getErrors()
    {
        clean();
        return _events.toArray(new _Error[_events.size()]);
    }

    @Override
    public synchronized Date getTime()
    {
        return _events.isEmpty() ? null : _events.getFirst().getTime();
    }

    @Override
    public synchronized String getMessage()
    {
        return _events.isEmpty() ? null : _events.getFirst().getMessage();
    }

    @Override
    public synchronized String getLevel()
    {
        return _events.isEmpty() ? null : _events.getFirst().getLevel();
    }

    @Override
    public synchronized void clear()
    {
        _events.clear();
    }
}
