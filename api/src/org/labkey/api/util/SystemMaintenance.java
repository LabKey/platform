/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.labkey.api.settings.AppProps;

import javax.servlet.ServletContextEvent;
import java.text.ParseException;
import java.util.*;

/**
 * User: adam
 * Date: Sep 29, 2006
 * Time: 2:18:53 PM
 */
public class SystemMaintenance extends TimerTask implements ShutdownListener
{
    private static final Object _timerLock = new Object();
    private static Timer _timer = null;
    private static SystemMaintenance _timerTask = null;
    private static final Logger _log = Logger.getLogger(SystemMaintenance.class);
    private static final List<MaintenanceTask> _tasks = new ArrayList<MaintenanceTask>(10);
    private static boolean _taskRunning = false;

    private boolean _checkTime;

    // Allows manual invocation of system maintenance tasks
    public SystemMaintenance(boolean checkTime)
    {
        _checkTime = checkTime;
    }

    public static void setTimer()
    {
        synchronized(_timerLock)
        {
            resetTimer();

            if (!"daily".equals(AppProps.getInstance().getSystemMaintenanceInterval()))
                return;

            // Create daemon timer for daily maintenance task
            _timer = new Timer("SystemMaintenance", true);

            // Timer has a single task that simply kicks off a thread that performs all the maintenance tasks.  This ensures that
            // the maintenance tasks run serially and will allow (if we need to in the future) to control the ordering (for example,
            // purge data first, then compact the database)
            _timerTask = new SystemMaintenance(true);
            ContextListener.addShutdownListener(_timerTask);
            _timer.scheduleAtFixedRate(_timerTask, getNextSystemMaintenanceTime(), DateUtils.MILLIS_PER_DAY);
        }
    }

    // Returns null if time can't be parsed in H:mm format
    public static Date parseSystemMaintenanceTime(String time)
    {
        try
        {
            return DateUtil.parseDateTime(time, "H:mm");
        }
        catch(ParseException e)
        {
            return null;
        }
    }

    // Returns null if time can't be parsed in H:mm format
    public static String formatSystemMaintenanceTime(Date time)
    {
        return DateUtil.formatDateTime(time, "H:mm");
    }

    private static Date getNextSystemMaintenanceTime()
    {
        int hour = 2;
        int minute = 0;

        Calendar time = Calendar.getInstance();
        Date mt = AppProps.getInstance().getSystemMaintenanceTime();

        // Shouldn't ever be null (we're parsing the property we wrote or the default value), but just in case use defaults
        if (null != mt)
        {
            time.setTime(mt);
            hour = time.get(Calendar.HOUR_OF_DAY);
            minute = time.get(Calendar.MINUTE);
        }

        Calendar nextTime = Calendar.getInstance();

        nextTime.set(Calendar.HOUR_OF_DAY, hour);
        nextTime.set(Calendar.MINUTE, minute);
        nextTime.set(Calendar.SECOND, 0);
        nextTime.set(Calendar.MILLISECOND, 0);

        // If we're about to schedule this for the past then roll up to tomorrow
        if (nextTime.before(Calendar.getInstance()))
            nextTime.add(Calendar.DATE, 1);

        return nextTime.getTime();
    }

    private static void resetTimer()
    {
        if (null != _timer)
            _timer.cancel();

        if (null != _timerTask)
            ContextListener.removeShutdownListener(_timerTask);
    }

    public static void addTask(MaintenanceTask task)
    {
        synchronized(_tasks)
        {
            _tasks.add(task);
        }
    }

    // Start a separate thread to do all the work
    public void run()
    {
        if (_checkTime && (System.currentTimeMillis() - scheduledExecutionTime()) > 2 * DateUtils.MILLIS_PER_HOUR)
        {
            _log.warn("Skipping system maintenance since it's two hours past the scheduled time");
        }
        else if (_taskRunning)
        {
            _log.warn("Skipping system maintenance since it's already running");
        }
        else
        {
            _taskRunning = true;
            Thread thread = new Thread(new Janitor(), "System Maintenance");
            thread.setDaemon(true);
            thread.start();
        }
    }


    public void shutdownPre(ServletContextEvent servletContextEvent)
    {
        
    }

    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        synchronized(_timerLock)
        {
            resetTimer();
        }
    }

    // Janitor runs each MaintenanceTask in order
    private class Janitor implements Runnable
    {
        public void run()
        {
            List<MaintenanceTask> tasksCopy;
            _log.info("System maintenance started");

            // Maintenance tasks could take a very long time to complete; make a copy so we don't block adds to the list
            // for the duration of all maintenance operations
            synchronized(_tasks)
            {
                tasksCopy = new ArrayList<MaintenanceTask>(_tasks);
            }

            for (MaintenanceTask task : tasksCopy)
            {
                _log.info(task.getMaintenanceTaskName() + " started");
                long start = System.currentTimeMillis();
                task.run();
                long elapsed = System.currentTimeMillis() - start;
                _log.info(task.getMaintenanceTaskName() + " complete; elapsed time " + elapsed/1000 + " seconds");
            }

            _log.info("System maintenance complete");           
            _taskRunning = false;
        }
    }

    public interface MaintenanceTask extends Runnable
    {
        public String getMaintenanceTaskName();
    }
}
