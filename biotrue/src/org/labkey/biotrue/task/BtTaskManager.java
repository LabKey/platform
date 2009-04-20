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

import org.labkey.api.data.*;
import org.labkey.biotrue.datamodel.Task;
import org.labkey.biotrue.datamodel.BtManager;
import org.labkey.biotrue.objectmodel.BtServer;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.Date;

public class BtTaskManager
{
    static private BtTaskManager instance;

    static private final Logger _log = Logger.getLogger(BtTaskManager.class);

    static public DbSchema getSchema()
    {
        return BtManager.get().getSchema();
    }

    public TableInfo getTable()
    {
        return BtManager.get().getTinfoTask();
    }

    synchronized static public BtTaskManager get()
    {
        if (instance != null)
            return instance;
        try
        {
            // If there are any tasks which were unfinished when the server died, they will have "started" not null.
            // null them out so they will run again.
            Table.execute(getSchema(), "UPDATE biotrue.task SET started = NULL", new Object[0]);
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
        }
        instance = new BtTaskManager();
        return instance;
    }

    synchronized public BtTask getNextTask(BtServer server)
    {
        try
        {
            SQLFragment sql = new SQLFragment("SELECT * FROM biotrue.task");
            sql.append("\nWHERE started IS NULL");
            if (server != null)
            {
                sql.append("\nAND serverid = " + server.getRowId());
            }
            sql.append("\nORDER BY RowId");
            DbSchema schema = BtManager.get().getSchema();
            sql = schema.getSqlDialect().limitRows(sql, 1);
            Task[] tasks = Table.executeQuery(schema, sql.getSQL(), sql.getParams().toArray(), Task.class);
            if (tasks.length < 1)
                return null;
            Task task = tasks[0];
            task.setStarted(new Date());
            task = Table.update(null, getTable(), task, task.getRowId(), null);
            notifyAll();
            return createTask(task);
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
            return null;
        }
    }

    synchronized public Task insertTask(Task task) throws SQLException
    {
        notifyAll();
        return Table.insert(null, getTable(), task);
    }

    synchronized public void taskComplete(Task task) throws SQLException
    {
        if (task.getStarted() == null)
            throw new IllegalStateException("Task was not started");
        Table.delete(getTable(), task.getRowId(), null);
    }

    private BtTask createTask(Task task)
    {
        switch(Operation.valueOf(task.getOperation()))
        {
            case download:
                return new DownloadTask(task);
            case view:
                return new BrowseTask(task);
        }
        throw new IllegalArgumentException(task.getOperation());
    }

    public boolean anyTasks(BtServer server)
    {
        SQLFragment sql = new SQLFragment("SELECT MIN(rowid) FROM biotrue.task WHERE serverid = ?");
        sql.add(server.getRowId());
        try
        {
            Integer rowId = Table.executeSingleton(getSchema(), sql.getSQL(), sql.getParams().toArray(), Integer.class);
            return rowId != null;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void cancelTasks(BtServer server) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ServerId", server.getRowId());
        Table.delete(getTable(), filter);
    }
}
