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

public class Entity
{
    int _rowId;
    int _serverId;
    int _parentId;
    String _biotrue_id;
    String _biotrue_type;
    String _biotrue_name;
    String _physicalName;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int id)
    {
        _rowId = id;
    }

    public int getServerId()
    {
        return _serverId;
    }

    public void setServerId(int id)
    {
        _serverId = id;
    }

    public int getParentId()
    {
        return _parentId;
    }

    public void setParentId(int id)
    {
        _parentId = id;
    }

    public String getBioTrue_Id()
    {
        return _biotrue_id;
    }

    public void setBioTrue_Id(String name)
    {
        _biotrue_id = name;
    }

    public String getBioTrue_Type()
    {
        return _biotrue_type;
    }

    public void setBioTrue_Type(String ent)
    {
        _biotrue_type = ent;
    }

    public String getBioTrue_Name()
    {
        return _biotrue_name;
    }

    public void setBioTrue_Name(String name)
    {
        _biotrue_name = name;
    }

    public String getPhysicalName()
    {
        return _physicalName;
    }

    public void setPhysicalName(String name)
    {
        _physicalName = name;
    }
}
