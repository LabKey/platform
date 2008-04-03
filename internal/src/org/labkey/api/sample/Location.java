/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.sample;

/**
 * Bean Class for for Inventory.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class Location
{
    private java.lang.String _ts = null;
    private int _createdBy = 0;
    private java.sql.Timestamp _created = null;
    private int _modifiedBy = 0;
    private java.sql.Timestamp _modified = null;

    private String _sampleLSID = null;
    private String freezer = null;
    private String rack = null;
    private String shelf = null;
    private String drawer = null;
    private String box = null;
    private int cell = 0;

    public Location()
    {
    }

    public java.lang.String getTs()
    {
        return _ts;
    }

    public void setTs(java.lang.String ts)
    {
        _ts = ts;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public java.sql.Timestamp getCreated()
    {
        return _created;
    }

    public void setCreated(java.sql.Timestamp created)
    {
        _created = created;
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public java.sql.Timestamp getModified()
    {
        return _modified;
    }

    public void setModified(java.sql.Timestamp modified)
    {
        _modified = modified;
    }

    public String getSampleLSID()
    {
        return _sampleLSID;
    }

    public void setSampleLSID(String sampleLSID)
    {
        _sampleLSID = sampleLSID;
    }

    public String getFreezer()
    {
        return freezer;
    }

    public void setFreezer(String freezer)
    {
        this.freezer = freezer;
    }

    public String getRack()
    {
        return rack;
    }

    public void setRack(String rack)
    {
        this.rack = rack;
    }

    public String getShelf()
    {
        return shelf;
    }

    public void setShelf(String shelf)
    {
        this.shelf = shelf;
    }

    public String getDrawer()
    {
        return drawer;
    }

    public void setDrawer(String drawer)
    {
        this.drawer = drawer;
    }

    public String getBox()
    {
        return box;
    }

    public void setBox(String box)
    {
        this.box = box;
    }

    public Integer getCell()
    {
        return cell;
    }

    public void setCell(Integer cell)
    {
        this.cell = cell == null ? 0 : cell;
    }
}
