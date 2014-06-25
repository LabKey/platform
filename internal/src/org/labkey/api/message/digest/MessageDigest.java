/*
 * Copyright (c) 2011-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.message.digest;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ShutdownListener;

import javax.servlet.ServletContextEvent;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: klum
 * Date: Jan 13, 2011
 * Time: 1:55:26 PM
 */
public abstract class MessageDigest
{
    private static final String LAST_KEY = "LastSuccessfulSend";
    private Timer _timer = null;
    private MessageDigestTask _timerTask = null;
    private final List<Provider> _providers = new CopyOnWriteArrayList<>();

    private static final Logger _log = Logger.getLogger(MessageDigest.class);

    public interface Provider
    {
        /**
         * Returns the list of containers that have messages within the date ranges to
         * include in the digest.
         */
        public List<Container> getContainersWithNewMessages(Date start, Date end) throws Exception;
        /**
         * Sends a digest for the container and date range
         */
        public void sendDigest(Container c, Date min, Date max) throws Exception;
    }

    public void addProvider(Provider provider)
    {
        _providers.add(provider);
    }

    public void sendMessageDigest() throws Exception
    {
        Date prev = getLastSuccessful();
        Date current = new Date();

        // get the start and end times to compute the message digest for
        Date start = getStartRange(current, prev);
        Date end = getEndRange(current, prev);

        for (Provider provider : _providers)
        {
            List<Container> containers = provider.getContainersWithNewMessages(start, end);

            for (Container c : containers)
                provider.sendDigest(c, start, end);
        }

        setLastSuccessful(end);
    }

    protected Date getStartRange(Date current, Date last)
    {
        if (last != null)
            return last;
        else
            return current;
    }

    protected Date getEndRange(Date current, Date last)
    {
        return current;
    }

    /**
     * The name of this digest type
     * @return
     */
    public abstract String getName();

    /**
     * Setup the initial timer for this digest, the interval at which this
     * timer should fire should be set up as well.
     */
    protected abstract Timer createTimer(TimerTask task);

    protected Date getLastSuccessful()
    {
        Map<String, String> props = PropertyManager.getProperties(getName());
        String value = props.get(LAST_KEY);
        return null != value ? new Date(Long.parseLong(value)) : null;
    }

    protected void setLastSuccessful(Date last)
    {
        Map<String, String> props = PropertyManager.getWritableProperties(getName(), true);
        props.put(LAST_KEY, String.valueOf(last.getTime()));
        PropertyManager.saveProperties(props);
    }

    public void initializeTimer()
    {
        _timerTask = new MessageDigestTask(this);
        ContextListener.addShutdownListener(_timerTask);

        _timer = createTimer(new MessageDigestTask(this));
    }

    private static class MessageDigestTask extends TimerTask implements ShutdownListener
    {
        private MessageDigest _digest;

        public MessageDigestTask(MessageDigest digest)
        {
            _digest = digest;
        }

        public void run()
        {
            _log.debug("Sending message digest");

            try
            {
                _digest.sendMessageDigest();
            }
            catch(Exception e)
            {
                ExceptionUtil.logExceptionToMothership(AppProps.getInstance().createMockRequest(), e);
            }
        }

        @Override
        public String getName()
        {
            return "Message digest timer task";
        }

        public void shutdownPre(ServletContextEvent servletContextEvent)
        {
            ContextListener.removeShutdownListener(_digest._timerTask);
            _digest._timer.cancel();
        }

        public void shutdownStarted(ServletContextEvent servletContextEvent)
        {
        }
    }
}
