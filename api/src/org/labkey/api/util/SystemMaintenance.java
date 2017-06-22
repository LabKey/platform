/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.PropertyManager;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Manages scheduling and queuing system maintenance tasks.
 * User: adam
 * Date: Sep 29, 2006
 */
public class SystemMaintenance
{
    private static final Object TIMER_LOCK = new Object();
    private static final List<MaintenanceTask> TASKS = new CopyOnWriteArrayList<>();
    private static final TriggerKey TRIGGER_KEY = new TriggerKey(SystemMaintenance.class.getCanonicalName());

    private volatile static boolean _timerDisabled = false;

    public static void setTimer()
    {
        synchronized(TIMER_LOCK)
        {
            try
            {
                Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

                // Clear previous job, if present
                if (scheduler.checkExists(TRIGGER_KEY))
                    scheduler.unscheduleJob(TRIGGER_KEY);

                if (_timerDisabled)
                    return;

                Calendar nextTime = getNextSystemMaintenanceTime();

                // Create a trigger scheduled for the same time every day
                Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_KEY)
                    .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(nextTime.get(Calendar.HOUR_OF_DAY), nextTime.get(Calendar.MINUTE)))
                    .build();

                // Create a quartz job that invokes a pipeline job that executes all the maintenance tasks
                JobDetail job = JobBuilder.newJob(SystemMaintenanceJob.class)
                    .withIdentity(SystemMaintenance.class.getCanonicalName())
                    .build();

                // Schedule trigger to execute the job every day at the appointed time
                scheduler.scheduleJob(job, trigger);
            }
            catch (SchedulerException e)
            {
                throw new RuntimeException("Failed to schedule system maintenance job", e);
            }
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
    public static @Nullable String formatSystemMaintenanceTime(Date time)
    {
        return DateUtil.formatDateTime(time, "H:mm");
    }

    private static Calendar getNextSystemMaintenanceTime()
    {
        Calendar time = Calendar.getInstance();
        Date mt = getProperties().getSystemMaintenanceTime();
        time.setTime(mt);

        return time;
    }

    public static void addTask(MaintenanceTask task)
    {
        if (task.getName().contains(","))
            throw new IllegalStateException("System maintenance task " + task.getClass().getSimpleName() + " has a comma in its name (" + task.getName() + ")");
        TASKS.add(task);
    }

    public static List<MaintenanceTask> getTasks()
    {
        return TASKS;
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
    private final static String ENABLED_TASKS_PROPERTY_NAME = "EnabledTasks";

    public static SystemMaintenanceProperties getProperties()
    {
        Map<String, String> props = PropertyManager.getProperties(SET_NAME);

        return new SystemMaintenanceProperties(props);
    }

    public static void setProperties(Set<String> enabledTasks, String time)
    {
        PropertyManager.PropertyMap writableProps = PropertyManager.getWritableProperties(SET_NAME, true);

        Set<String> enabled = new HashSet<>(enabledTasks);
        Set<String> disabled = getTasks().stream()
            .filter(task -> task.canDisable() && !enabled.contains(task.getName()))
            .map(MaintenanceTask::getName)
            .collect(Collectors.toSet());

        writableProps.put(TIME_PROPERTY_NAME, time);
        writableProps.put(DISABLED_TASKS_PROPERTY_NAME, StringUtils.join(disabled, ","));
        writableProps.put(ENABLED_TASKS_PROPERTY_NAME, StringUtils.join(enabled, ","));

        writableProps.save();
        setTimer();
    }

    public static class SystemMaintenanceProperties
    {
        private final Date _systemMaintenanceTime;
        private final Set<String> _disabledTasks = new HashSet<>();

        private SystemMaintenanceProperties(Map<String, String> props)
        {
            Set<String> enabledTasks = new HashSet<>();
            Date time = SystemMaintenance.parseSystemMaintenanceTime(props.get(TIME_PROPERTY_NAME));
            _systemMaintenanceTime = (null == time ? SystemMaintenance.parseSystemMaintenanceTime("2:00") : time);

            String disabled = props.get(DISABLED_TASKS_PROPERTY_NAME);
            String enabled = props.get(ENABLED_TASKS_PROPERTY_NAME);

            if (disabled != null)
                _disabledTasks.addAll(Arrays.asList(disabled.split(",")));
            if (enabled != null)
                enabledTasks.addAll(Arrays.asList(enabled.split(",")));

            // set the default disabled state if the task has not explicitly been set
            getTasks()
                .stream()
                .filter(task -> !_disabledTasks.contains(task.getName()) && !enabledTasks.contains(task.getName()))
                .filter(task -> task.canDisable() && !task.isEnabledByDefault())
                .forEach(task -> _disabledTasks.add(task.getName()));
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

    /**
     * A specific piece of maintenance to be run as part of the overall {@link MaintenancePipelineJob}.
     */
    public interface MaintenanceTask
    {
        /** Description used in logging and UI */
        String getDescription();

        /**
         * Short name used in forms and to persist disabled settings.
         * Task name must be unique and cannot contain a comma
         */
        String getName();

        /**
         * Perform the maintenance. Logger provides access to the pipeline log, see #28464.
         *
         * @param log Logger for maintenance tasks to use
         */
        void run(Logger log);

        /** Can this task be disabled? */
        default boolean canDisable()
        {
            return true;
        }

        /**
         * returns the default enabled state
         */
        default boolean isEnabledByDefault()
        {
            return true;
        }

        /** Hide this from the Admin page (because it will be controlled from elsewhere) */
        default boolean hideFromAdminPage()
        {
            return false;
        }
    }
}
