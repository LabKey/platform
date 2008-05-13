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

package org.labkey.biotrue.datamodel;

import java.util.Date;

public class Log
{
    int rowId;

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public int getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(int sessionId)
    {
        this.sessionId = sessionId;
    }

    public int getEntityId()
    {
        return entityId;
    }

    public void setEntityId(int entityId)
    {
        this.entityId = entityId;
    }

    public Date getStarted()
    {
        return new Date(started);
    }

    public void setStarted(Date started)
    {
        this.started = started.getTime();
    }

    public Date getFinished()
    {
        return new Date(finished);
    }

    public void setFinished(Date finished)
    {
        this.finished = finished.getTime();
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public void setTask(String task)
    {
        this.task = task;
    }

    public String getTask()
    {
        return this.task;
    }

    public String getNotes()
    {
        return notes;
    }

    public void setNodes(String notes)
    {
        this.notes = notes;
    }

    int sessionId;
    int entityId;
    long started;
    long finished;
    String task;
    String status;
    String notes;
}
