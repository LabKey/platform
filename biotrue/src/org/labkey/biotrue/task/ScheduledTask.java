/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.biotrue.task;

import org.apache.log4j.Logger;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.security.User;
import org.labkey.api.data.Table;
import org.labkey.biotrue.datamodel.Server;
import org.labkey.biotrue.datamodel.BtManager;
import org.labkey.biotrue.datamodel.Task;
import org.labkey.biotrue.objectmodel.BtServer;

import javax.servlet.ServletContextEvent;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Apr 2, 2007
 */
public class ScheduledTask extends TimerTask implements ShutdownListener
{
    static final private Logger _log = Logger.getLogger(ScheduledTask.class);
    private static Timer _timer = null;
    private static Map<Integer, TimerTask> _timerTasks = new HashMap<Integer, TimerTask>();
    private static ScheduledTask _instance = new ScheduledTask();

    private int _serverId;

    private ScheduledTask(){}

    public static ScheduledTask getInstance()
    {
        return _instance;
    }

    public synchronized void startTimer()
    {
        if (_timer == null)
        {
            _timer = new Timer("ScheduledTaskTimer", true);
            ContextListener.addShutdownListener(this);

            try {
                // start any scheduled tasks
                for (Server server : Table.select(BtManager.get().getTinfoServer(), Table.ALL_COLUMNS, null, null, Server.class))
                {
                    Date nextTime = server.getNextSync();
                    if (nextTime != null)
                    {
                        // if the next sync time has already passed, start the sync task, else reset to
                        // the old time.
                        Calendar current = Calendar.getInstance();
                        if (current.getTime().after(nextTime))
                        {
                            current.add(Calendar.MINUTE, 5);
                            setTask(null, server, current.getTime());
                        }
                        else
                            setTask(null, server, nextTime);
                    }
                    else
                        setTask(null, server, null);
                }
            }
            catch (Exception e)
            {
                _log.error("An error occurred initializing the scheduled sync tasks", e);
            }
        }
    }

    public void setTask(User user, Server server, Date nextTime)
    {
        // remove an existing task
        if (_timerTasks.containsKey(server.getRowId()))
        {
            _timerTasks.get(server.getRowId()).cancel();
            _timerTasks.remove(server.getRowId());
        }

        if (server.getSyncInterval() > 0)
        {
            try {
                if (nextTime == null)
                {
                    Calendar next = Calendar.getInstance();
                    next.add(Calendar.HOUR_OF_DAY, server.getSyncInterval());
                    nextTime = next.getTime();
                }
                server.setNextSync(nextTime);
                BtManager.get().updateServer(user, server);

                TimerTask task = new ScheduledTask(server.getRowId());
                _timerTasks.put(server.getRowId(), task);
                _timer.schedule(task, nextTime);
            }
            catch (Exception e)
            {
                _log.error("Unable to start a scheduled task", e);
            }
        }
    }

    private ScheduledTask(int serverId)
    {
        _serverId = serverId;
    }

    public void run()
    {
        BtServer server = BtServer.fromId(_serverId);
        if (server != null)
        {
            try {
                if (!BtTaskManager.get().anyTasks(server))
                {
                    _log.debug("Running a scheduled sync task for server: " + server.getName());
                    Task task = new Task();
                    task.setServerId(server.getRowId());
                    task.setOperation(Operation.view.toString());
                    new BrowseTask(task).doRun();
                }
            }
            catch (Exception e)
            {
                _log.error("An error occurred running a scheduled sync task for server: " + server.getName(), e);
            }
            finally
            {
                setTask(null, server._server, null);
            }
        }
    }

    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        ContextListener.removeShutdownListener(this);
        _timer.cancel();

        for (TimerTask timer : _timerTasks.values())
            timer.cancel();
    }
}
