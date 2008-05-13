/*
 * Copyright (c) 2007 LabKey Corporation
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

import org.labkey.biotrue.datamodel.Task;
import org.labkey.biotrue.soapmodel.Browse_response;
import org.labkey.biotrue.objectmodel.BtServer;
import org.labkey.biotrue.objectmodel.BtEntity;
import org.apache.log4j.Logger;

import java.sql.SQLException;

abstract public class BtTask
{
    private final Logger __log = Logger.getLogger(BtTask.class);
    private Task _task;
    private String _sessionId;

    protected BtTask(Task task)
    {
        _task = task;
    }

    public Task getTask()
    {
        return _task;
    }

    public BtServer getServer()
    {
        return BtServer.fromId(_task.getServerId());
    }

    public BtEntity getEntity()
    {
        return BtEntity.fromId(_task.getEntityId());
    }

    public String getOperation()
    {
        return _task.getOperation();
    }

    protected Browse_response loginBrowse(BtEntity entity) throws Exception
    {
        if (_sessionId != null)
            throw new IllegalStateException("Already logged in");
        Browse_response ret = getServer().loginBrowse(entity);
        _sessionId = ret.getData().getSession_id();
        return ret;
    }

    protected void logout()
    {
        if (_sessionId == null)
        {
            return;
        }
        try
        {
            getServer().logout(_sessionId);
        }
        catch (Throwable t)
        {
            __log.error("Error", t);
        }
    }

    abstract public void doRun() throws Exception;

    final public void run() throws Exception
    {
        try
        {
            doRun();
        }
        finally
        {
            logout();
        }
    }
    protected Task newTask(BtEntity entity, Operation operation) throws SQLException
    {
        Task ret = new Task();
        ret.setServerId(_task.getServerId());
        if (entity != null)
        {
            ret.setEntityId(entity.getRowId());
        }
        ret.setOperation(operation.toString());
        return BtTaskManager.get().insertTask(ret);
    }
}
