/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.StatusAppender;
import org.labkey.api.action.StatusReportingRunnable;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.view.ViewServlet;

import javax.servlet.ServletContextEvent;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: adam
 * Date: Sep 29, 2006
 * Time: 2:18:53 PM
 */
public class SystemMaintenance extends TimerTask implements ShutdownListener, StatusReportingRunnable
{
    private static final Object _timerLock = new Object();
    private static final List<MaintenanceTask> _tasks = new CopyOnWriteArrayList<>();

    private static Timer _timer = null;
    private static SystemMaintenance _timerTask = null;
    private static boolean _taskRunning = false;

    private volatile static boolean _timerDisabled = false;

    private final @Nullable String _taskName;
    private final Logger _log;
    private final boolean _temp;

    private StatusAppender _appender;

    // temp = true allows manual invocation of system maintenance tasks.  Prevents time check and creates a logger that
    // can report status back to request threads.
    public SystemMaintenance(boolean temp, @Nullable String taskName)
    {
        _log = Logger.getLogger(SystemMaintenance.class);
        _taskName = taskName;

        if (temp)
        {
            _appender = new StatusAppender();
            _log.addAppender(_appender);
        }

        _temp = temp;
    }

    public static void setTimer()
    {
        synchronized(_timerLock)
        {
            resetTimer();

            if (_timerDisabled)
                return;

            // Create daemon timer for daily maintenance task
            _timer = new Timer("SystemMaintenance", true);

            // Timer has a single task that simply kicks off a thread that performs all the maintenance tasks.  This ensures that
            // the maintenance tasks run serially and will allow (if we need to in the future) to control the ordering (for example,
            // purge data first, then compact the database)
            _timerTask = new SystemMaintenance(false, null);
            ContextListener.addShutdownListener(_timerTask);
            _timer.scheduleAtFixedRate(_timerTask, getNextSystemMaintenanceTime(), DateUtils.MILLIS_PER_DAY);
        }
    }

    // Returns null if time can't be parsed in h:mm a or H:mm format
    public static @Nullable Date parseSystemMaintenanceTime(String time)
    {
        Date date = null;

        try
        {
            date = DateUtil.parseDateTime(time, "h:mm a");
        }
        catch(ParseException e)
        {
        }

        if (null == date)
        {
            try
            {
                return DateUtil.parseDateTime(time, "H:mm");
            }
            catch(ParseException e)
            {
            }
        }

        return date;
    }

    // Returns null if time is null
    public static String formatSystemMaintenanceTime(Date time)
    {
        return DateUtil.formatDateTime(time, "H:mm");
    }

    private static Date getNextSystemMaintenanceTime()
    {
        Calendar time = Calendar.getInstance();
        Date mt = getProperties().getSystemMaintenanceTime();
        time.setTime(mt);

        Calendar nextTime = Calendar.getInstance();

        nextTime.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY));
        nextTime.set(Calendar.MINUTE,  time.get(Calendar.MINUTE));
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
        if (task.getName().contains(","))
            throw new IllegalStateException("System maintenance task " + task.getClass().getSimpleName() + " has a comma in its name (" + task.getName() + ")");
        _tasks.add(task);
    }

    public static List<MaintenanceTask> getTasks()
    {
        return _tasks;
    }

    public static boolean isTimerDisabled()
    {
        return _timerDisabled;
    }

    public static void setTimeDisabled(boolean disable)
    {
        _timerDisabled = disable;
    }

    private final static String SET_NAME = "SystemMaintenance";
    private final static String TIME_PROPERTY_NAME = "MaintenanceTime";
    private final static String DISABLED_TASKS_PROPERTY_NAME = "DisabledTasks";

    public static SystemMaintenanceProperties getProperties()
    {
        Map<String, String> props = PropertyManager.getProperties(SET_NAME);

        return new SystemMaintenanceProperties(props);
    }

    public static void setProperties(Set<String> enabledTasks, String time)
    {
        PropertyManager.PropertyMap writableProps = PropertyManager.getWritableProperties(SET_NAME, true);

        Set<String> enabled = new HashSet<>(enabledTasks);
        Set<String> disabled = new HashSet<>();

        for (MaintenanceTask task : getTasks())
            if (task.canDisable() && !enabled.contains(task.getName()))
                disabled.add(task.getName());

        writableProps.put(TIME_PROPERTY_NAME, time);
        writableProps.put(DISABLED_TASKS_PROPERTY_NAME, StringUtils.join(disabled, ","));

        PropertyManager.saveProperties(writableProps);
        setTimer();
    }

    public static class SystemMaintenanceProperties
    {
        private Date _systemMaintenanceTime;
        private Set<String> _disabledTasks;

        private SystemMaintenanceProperties(Map<String, String> props)
        {
            Date time = SystemMaintenance.parseSystemMaintenanceTime(props.get(TIME_PROPERTY_NAME));
            _systemMaintenanceTime = (null == time ? SystemMaintenance.parseSystemMaintenanceTime("2:00") : time);

            String disabled = props.get(DISABLED_TASKS_PROPERTY_NAME);
            _disabledTasks = (null == disabled ? Collections.<String>emptySet() : new HashSet<>(Arrays.asList(disabled.split(","))));
        }

        public @NotNull Date getSystemMaintenanceTime()
        {
            return _systemMaintenanceTime;
        }

        public @NotNull Set<String> getDisabledTasks()
        {
            return _disabledTasks;
        }
    }

    // Start a separate thread to do all the work
    public void run()
    {
        if (!_temp && (System.currentTimeMillis() - scheduledExecutionTime()) > 2 * DateUtils.MILLIS_PER_HOUR)
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

    @Override
    public boolean isRunning()
    {
        return _taskRunning;
    }

    @Override
    public Collection<String> getStatus(@Nullable Integer offset)
    {
        return _appender.getStatus(offset); 
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
            _log.info("System maintenance started");

            Set<String> disabledTasks = getProperties().getDisabledTasks();

            for (MaintenanceTask task : _tasks)
            {
                if (ViewServlet.isShuttingDown())
                {
                    _log.info("System maintenance is stopping due to server shut down");
                    break;
                }

                if (null != _taskName && !_taskName.isEmpty())
                {
                    // If _taskName is set, then admin has invoked a single task from the UI... skip all the others
                    if (!task.getName().equals(_taskName))
                        continue;
                }
                else
                {
                    // Run all tasks, except for disabled
                    if (task.canDisable() && disabledTasks.contains(task.getName()))
                        continue;
                }

                _log.info(task.getDescription() + " started");
                long start = System.currentTimeMillis();

                try
                {
                    task.run();
                }
                catch (Exception e)
                {
                    // Log if one of these tasks throws... but continue with other tasks
                    ExceptionUtil.logExceptionToMothership(null, e);
                }

                long elapsed = System.currentTimeMillis() - start;
                _log.info(task.getDescription() + " complete; elapsed time " + elapsed/1000 + " seconds");
            }

            _log.info("System maintenance complete");           
            _taskRunning = false;
        }
    }

    public interface MaintenanceTask extends Runnable
    {
        // Description used in logging and UI
        public String getDescription();

        // Short name used in forms and to persist disabled settings
        // Task name must be unique and cannot contain a comma
        public String getName();

        // Can this task be disabled?
        public boolean canDisable();

        // Hide this from the Admin page (because it will be controlled from elsewhere)
        public boolean hideFromAdminPage();
    }
}
