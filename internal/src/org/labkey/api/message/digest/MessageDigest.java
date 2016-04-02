/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
import org.labkey.api.data.PropertyManager;
import org.labkey.api.util.ExceptionUtil;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: klum
 * Date: Jan 13, 2011
 * Time: 1:55:26 PM
 */
public abstract class MessageDigest
{
    private static final String LAST_KEY = "LastSuccessfulSend";
    private static final String MESSAGE_DIGEST_KEY = "MessageDigest";

    private final List<Provider> _providers = new CopyOnWriteArrayList<>();

    private static final Logger _log = Logger.getLogger(MessageDigest.class);

    public interface Provider
    {
        /**
         * Returns the list of containers that have messages within the date ranges to
         * include in the digest.
         */
        void sendDigestForAllContainers(Date start, Date end) throws Exception;
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
            provider.sendDigestForAllContainers(start, end);
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
     * @return Digest name
     */
    public abstract String getName();

    /**
     * Returns a TriggerBuilder configured with the necessary schedule for this digest.
     */
    protected abstract Trigger getTrigger();

    private Date getLastSuccessful()
    {
        Map<String, String> props = PropertyManager.getProperties(getName());
        String value = props.get(LAST_KEY);
        return null != value ? new Date(Long.parseLong(value)) : null;
    }

    private void setLastSuccessful(Date last)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(getName(), true);
        props.put(LAST_KEY, String.valueOf(last.getTime()));
        props.save();
    }

    public void initializeTimer()
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            // Get configured quartz Trigger from subclass
            Trigger trigger = getTrigger();

            // Quartz Job that sends the digest
            JobDetail job = JobBuilder.newJob(MessageDigestJob.class).build();

            // Add this MessageDigest instance to the Job context so the Job knows which digest to send
            job.getJobDataMap().put(MESSAGE_DIGEST_KEY, this);

            // Schedule trigger to execute the message digest job on the configured schedule
            scheduler.scheduleJob(job, trigger);
        }
        catch (SchedulerException e)
        {
            throw new RuntimeException("Failed to schedule message digest job", e);
        }
    }

    @SuppressWarnings("WeakerAccess") // Must be public so Quartz can construct via reflection
    public static class MessageDigestJob implements Job
    {
        public MessageDigestJob()
        {
        }

        public void execute(JobExecutionContext context) throws JobExecutionException
        {
            try
            {
                JobDataMap map = context.getJobDetail().getJobDataMap();
                MessageDigest digest = (MessageDigest)map.get(MESSAGE_DIGEST_KEY);
                _log.debug("Sending message digest for " + digest.getName());
                digest.sendMessageDigest();
            }
            catch(Exception e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }
}
