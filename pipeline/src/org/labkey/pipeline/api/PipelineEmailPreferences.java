/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.pipeline.api;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ShutdownListener;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * User: Karl Lum
 * Date: Jan 24, 2007
 */
public class PipelineEmailPreferences
{
    private static final String PIPELINE_NOTIFICATION_TASKS = "PipelineNotificationTasks";
    private static final Logger _log = Logger.getLogger(PipelineEmailPreferences.class);

    public static final String PREF_NOTIFY_OWNER_ON_SUCCESS = "notifyOwnerOnSuccess";
    public static final String PREF_NOTIFY_USERS_ON_SUCCESS = "notifyUsersOnSuccess";
    public static final String PREF_NOTIFY_OWNER_ON_ERROR = "notifyOwnerOnError";
    public static final String PREF_NOTIFY_USERS_ON_ERROR = "notifyUsersOnError";
    public static final String PREF_ESCALATION_USERS = "escalationUsers";
    public static final String PREF_SUCCESS_INTERVAL = "successNotificationInterval";
    public static final String PREF_FAILURE_INTERVAL = "failureNotificationInterval";
    public static final String PREF_SUCCESS_NOTIFY_START = "successNotificationStart";
    public static final String PREF_FAILURE_NOTIFY_START = "failureNotificationStart";
    public static final String LAST_SUCCESS_NOTIFICATION = "lastSuccessNotification";
    public static final String LAST_FAILURE_NOTIFICATION = "lastFailureNotification";

    private static Timer _timer;
    private static Map<String, TimerTask> _timerTasks = new HashMap<>();

    private static PipelineEmailPreferences _instance = new PipelineEmailPreferences();
    private PipelineEmailPreferences(){}

    public static PipelineEmailPreferences get(){return _instance;}

    public void startNotificationTasks()
    {
        Map<String, String> taskMap = PropertyManager.getProperties(PIPELINE_NOTIFICATION_TASKS);

        for (Map.Entry<String, String> entry : taskMap.entrySet())
        {
            Container c = ContainerManager.getForId(entry.getValue());
            if (c != null)
            {
                if (isSuccessKey(entry.getKey()))
                    setNotificationTask(getSuccessNotificationInterval(c), getSuccessNotifyStart(c), c, true);
                else
                    setNotificationTask(getFailureNotificationInterval(c), getFailureNotifyStart(c), c, false);
            }
        }
    }

    public boolean getNotifyOwnerOnSuccess(Container c)
    {
        return BooleanUtils.toBoolean(_getProperty(c, PREF_NOTIFY_OWNER_ON_SUCCESS));
    }

    public void setNotifyOwnerOnSuccess(boolean value, Container c)
    {
        if (value != getNotifyOwnerOnSuccess(c))
            _setProperty(c, PREF_NOTIFY_OWNER_ON_SUCCESS, BooleanUtils.toStringTrueFalse(value));
    }

    public boolean getNotifyOwnerOnError(Container c)
    {
        return BooleanUtils.toBoolean(_getProperty(c, PREF_NOTIFY_OWNER_ON_ERROR));
    }

    public void setNotifyOwnerOnError(boolean value, Container c)
    {
        if (value != getNotifyOwnerOnError(c))
            _setProperty(c, PREF_NOTIFY_OWNER_ON_ERROR, BooleanUtils.toStringTrueFalse(value));
    }

    public String getNotifyUsersOnSuccess(Container c)
    {
        return _getProperty(c, PREF_NOTIFY_USERS_ON_SUCCESS);
    }

    public void setNotifyUsersOnSuccess(String value, Container c)
    {
        if (!StringUtils.equals(getNotifyUsersOnSuccess(c), value))
            _setProperty(c, PREF_NOTIFY_USERS_ON_SUCCESS, value);
    }

    public String getNotifyUsersOnError(Container c)
    {
        return _getProperty(c, PREF_NOTIFY_USERS_ON_ERROR);
    }

    public void setNotifyUsersOnError(String value, Container c)
    {
        if (!StringUtils.equals(getNotifyUsersOnError(c), value))
            _setProperty(c, PREF_NOTIFY_USERS_ON_ERROR, value);
    }

    public String getEscalationUsers(Container c)
    {
        return _getProperty(c, PREF_ESCALATION_USERS);
    }

    public void setEscalationUsers(String value, Container c)
    {
        if (!StringUtils.equals(getEscalationUsers(c), value))
            _setProperty(c, PREF_ESCALATION_USERS, value);
    }

    public String getSuccessNotificationInterval(Container c)
    {
        return _getProperty(c, PREF_SUCCESS_INTERVAL);
    }

    public void setSuccessNotificationInterval(String value, String startTime, Container c)
    {
        boolean dirty = false;
        if (!StringUtils.equals(getSuccessNotificationInterval(c), value))
        {
            _setProperty(c, PREF_SUCCESS_INTERVAL, value);
            dirty = true;
        }
        if (!StringUtils.equals(getSuccessNotifyStart(c), startTime))
        {
            _setProperty(c, PREF_SUCCESS_NOTIFY_START, startTime);
            dirty = true;
        }

        if (dirty)
            setNotificationTask(value, startTime, c, true);
    }

    public String getFailureNotificationInterval(Container c)
    {
        return _getProperty(c, PREF_FAILURE_INTERVAL);
    }

    public void setFailureNotificationInterval(String value, String startTime, Container c)
    {
        boolean dirty = false;
        if (!StringUtils.equals(getFailureNotificationInterval(c), value))
        {
            _setProperty(c, PREF_FAILURE_INTERVAL, value);
            dirty = true;
        }
        if (!StringUtils.equals(getFailureNotifyStart(c), startTime))
        {
            _setProperty(c, PREF_FAILURE_NOTIFY_START, startTime);
            dirty = true;
        }

        if (dirty)
            setNotificationTask(value, startTime, c, false);
    }

    public String getSuccessNotifyStart(Container c)
    {
        return _getProperty(c, PREF_SUCCESS_NOTIFY_START);
    }

    public String getFailureNotifyStart(Container c)
    {
        return _getProperty(c, PREF_FAILURE_NOTIFY_START);
    }

    private Date getLastSuccessful(Container c, boolean isSuccessNotification)
    {
        final String propName = isSuccessNotification ? LAST_SUCCESS_NOTIFICATION : LAST_FAILURE_NOTIFICATION;
        String value = _getProperty(c, propName);
        return null != value ? new Date(Long.parseLong(value)) : null;
    }

    private void setLastSuccessful(Date last, Container c, boolean isSuccessNotification)
    {
        final String propName = isSuccessNotification ? LAST_SUCCESS_NOTIFICATION : LAST_FAILURE_NOTIFICATION;
        _setProperty(c, propName, String.valueOf(last.getTime()));
    }

    public void deleteAll(Container c)
    {
        _setProperty(c, PREF_NOTIFY_OWNER_ON_SUCCESS, null);
        _setProperty(c, PREF_NOTIFY_USERS_ON_SUCCESS, null);
        _setProperty(c, PREF_NOTIFY_OWNER_ON_ERROR, null);
        _setProperty(c, PREF_NOTIFY_USERS_ON_ERROR, null);
        _setProperty(c, PREF_ESCALATION_USERS, null);
        _setProperty(c, PREF_SUCCESS_INTERVAL, null);
        _setProperty(c, PREF_FAILURE_INTERVAL, null);
        _setProperty(c, PREF_SUCCESS_NOTIFY_START, null);
        _setProperty(c, PREF_FAILURE_NOTIFY_START, null);

        removeNotifyTask(c, true);
        removeNotifyTask(c, false);
    }

    public boolean isInherited(Container container, String name)
    {
        if (container == null || container.isRoot())
            return false;

        Container owningContainer = _findContainerFor(container, name);
        return (owningContainer != null && !container.equals(owningContainer));
    }

    private String _getProperty(Container c, String name)
    {
        // allow properties to be set site-wide or per pipeline root level
        do
        {
            String prop = PipelineService.get().getPipelineProperty(c, name);
            if (prop != null)
            {
                return prop;
            }
            c = c.getParent();
        }
        while (null != c && !c.isRoot());

        return PipelineService.get().getPipelineProperty(ContainerManager.getRoot(), name);
    }

    private Container _findContainerFor(Container c, String name)
    {
        // allow properties to be set site-wide or per pipeline root level
        do
        {
            String prop = PipelineService.get().getPipelineProperty(c, name);
            if (prop != null)
            {
                return c;
            }
            c = c.getParent();
        }
        while (!c.isRoot());

        String prop = PipelineService.get().getPipelineProperty(ContainerManager.getRoot(), name);
        if (prop != null)
            return ContainerManager.getRoot();
        return null;
    }

    private void _setProperty(Container c, String name, String value)
    {
        if ("".equals(value))
            value = null;
        PipelineService.get().setPipelineProperty(c, name, value);
    }

    private void setNotificationTask(String interval, String startTime, Container c, boolean isSuccessNotification)
    {
        int intervalInHours = NumberUtils.toInt(interval);
        if (intervalInHours > 0 && startTime != null)
        {
            try {
                Date date = DateUtil.parseDateTime(startTime, "H:mm");
                Date nextTime = getNextNotification(date, intervalInHours);

                addNotifyTask(c, intervalInHours, isSuccessNotification, nextTime);
            }
            catch (ParseException pe)
            {
                _log.error("Unable to create pipeline email notification timer task", pe);
            }
        }
        else
            removeNotifyTask(c, isSuccessNotification);
    }

    private synchronized void addNotifyTask(Container c, int intervalInHours, boolean isSuccessNotification, Date nextTime)
    {
        PropertyManager.PropertyMap taskMap = PropertyManager.getWritableProperties(PIPELINE_NOTIFICATION_TASKS, true);
        final String key = getKey(c, isSuccessNotification);

        taskMap.put(key, c.getId());
        if (_timer == null)
        {
            _timer = new Timer("pipelineEmailNotification", true);
        }

        TimerTask task = _timerTasks.get(key);
        if (task != null)
            task.cancel();

        task = new EmailNotifyTask(c, intervalInHours, isSuccessNotification);
        _timerTasks.put(key, task);
        _timer.scheduleAtFixedRate(task, nextTime, intervalInHours * 60 * 60 * 1000);
        taskMap.save();
    }

    private String getKey(Container c, boolean isSuccessNotification)
    {
        return c.getId() + '/' + isSuccessNotification;
    }

    private boolean isSuccessKey(String key)
    {
        return key.endsWith(String.valueOf(true));
    }
    
    private synchronized void removeNotifyTask(Container c, boolean isSuccessNotification)
    {
        PropertyManager.PropertyMap taskMap = PropertyManager.getWritableProperties(PIPELINE_NOTIFICATION_TASKS, true);
        final String key = getKey(c, isSuccessNotification);

        if (taskMap.containsKey(key))
        {
            taskMap.remove(key);
            taskMap.save();
        }
        TimerTask task = _timerTasks.get(key);
        if (task != null)
        {
            task.cancel();
            _timerTasks.remove(key);
        }
    }

    private Date getNextNotification(Date start, int interval)
    {
        if (interval > 0)
        {
            Calendar startTime = Calendar.getInstance();
            startTime.setTime(start);

            Calendar next = Calendar.getInstance();
            next.set(Calendar.HOUR_OF_DAY, startTime.get(Calendar.HOUR_OF_DAY));
            next.set(Calendar.MINUTE, startTime.get(Calendar.MINUTE));
            
            Calendar current = Calendar.getInstance();
            // find the next scheduled interval after the current date
            while (next.before(current))
            {
                next.add(Calendar.HOUR_OF_DAY, interval);
            }
            return next.getTime();
        }
        return start;
    }

    private static class EmailNotifyTask extends TimerTask implements ShutdownListener
    {
        private final Container _c;
        private final int _interval;
        /** notification task for success or failure of jobs */
        private final boolean _isSuccessNotification;

        public EmailNotifyTask(Container c, int interval, boolean isSuccessNotification)
        {
            _c = c;
            _interval = interval;
            _isSuccessNotification = isSuccessNotification;
        }

        public void run()
        {
            try
            {
                Date min = PipelineEmailPreferences.get().getLastSuccessful(_c, _isSuccessNotification);
                if (min == null)
                {
                    Calendar current = Calendar.getInstance();
                    current.add(Calendar.HOUR_OF_DAY, -(_interval));
                    min = current.getTime();
                }
                Date max = Calendar.getInstance().getTime();
                SimpleFilter filter = new SimpleFilter();

                if (_isSuccessNotification)
                    filter.addCondition(FieldKey.fromParts("Status"), PipelineJob.TaskStatus.complete.toString(), CompareType.EQUAL);
                else
                {
                    filter.addWhereClause("Status IN (?, ?)",
                            new Object[]{PipelineJob.TaskStatus.error.toString(), PipelineJob.TaskStatus.cancelled.toString()});
                }

                if (!_c.isRoot())
                    filter.addCondition(FieldKey.fromParts("container"), _c, CompareType.EQUAL);
                filter.addCondition(FieldKey.fromParts("modified"), min, CompareType.GTE);
                filter.addCondition(FieldKey.fromParts("modified"), max, CompareType.LT);

                PipelineStatusFileImpl[] files;
                try (DbScope.Transaction transaction = PipelineStatusManager.getTableInfo().getSchema().getScope().ensureTransaction(new PipelineStatusManager.PipelineStatusTransactionKind()))
                {
                    files = new TableSelector(PipelineStatusManager.getTableInfo(), filter, null).getArray(PipelineStatusFileImpl.class);
                    transaction.commit();
                }

                if (files.length > 0)
                {
                    PipelineManager.sendNotificationEmail(files, _c, min, max, _isSuccessNotification);
                    _log.debug("sending pipeline email notifications");
                }
                PipelineEmailPreferences.get().setLastSuccessful(max, _c, _isSuccessNotification);
            }
            catch (Exception e)
            {
                _log.error("Unable to send pipeline email notification", e);
                //ExceptionUtil.logExceptionToMothership(request, e);
            }
        }

        @Override
        public String getName()
        {
            return "Pipeline email notification timer";
        }

        public void shutdownPre()
        {
            ContextListener.removeShutdownListener(this);
            _timer.cancel();
        }

        public void shutdownStarted()
        {
        }
    }
}
