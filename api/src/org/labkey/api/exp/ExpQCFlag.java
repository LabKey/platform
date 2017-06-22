/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.exp;

import org.labkey.api.data.Entity;

/**
 * Quality control flag bean class, attached to an {@link org.labkey.api.exp.api.ExpRun}.
 * User: cnathe
 * Date: Dec 16, 2011
 */
public class ExpQCFlag extends Entity
{
    private int _rowId;
    private int _runId;
    private String _flagType;
    private String _description;
    private String _comment;
    private boolean _enabled;
    private int _intKey1;
    private int _intKey2;
    private String _key1;
    private String _key2;

    public ExpQCFlag() {}

    public ExpQCFlag(int runId, String flagType, String description)
    {
        _runId = runId;
        _flagType = flagType;
        _description = description;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getRunId()
    {
        return _runId;
    }

    public void setRunId(int runId)
    {
        _runId = runId;
    }

    public String getFlagType()
    {
        return _flagType;
    }

    public void setFlagType(String flagType)
    {
        _flagType = flagType;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getComment()
    {
        return _comment;
    }

    public void setComment(String comment)
    {
        _comment = comment;
    }

    public boolean isEnabled()
    {
        return _enabled;
    }

    public void setEnabled(boolean enabled)
    {
        _enabled = enabled;
    }

    public int getIntKey1()
    {
        return _intKey1;
    }

    public void setIntKey1(int intKey1)
    {
        _intKey1 = intKey1;
    }

    public int getIntKey2()
    {
        return _intKey2;
    }

    public void setIntKey2(int intKey2)
    {
        _intKey2 = intKey2;
    }

    public String getKey1()
    {
        return _key1;
    }

    public void setKey1(String key1)
    {
        _key1 = key1;
    }

    public String getKey2()
    {
        return _key2;
    }

    public void setKey2(String key2)
    {
        _key2 = key2;
    }
}