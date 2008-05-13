/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.util.ResultSetUtil;

/**
 * Bean Class for for StorageLocation.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class StorageLocation
{

    private java.lang.String _locationId = null;
    private java.lang.String _parentStorageLocation = null;
    private String _parentStorageLocationDisplay = null;
    private java.lang.String _locationName = null;
    private boolean _isparent = false;
    private int _maxItemsInBox = 0;

    public StorageLocation()
    {
    }

    public java.lang.String getLocationId()
    {
        return _locationId;
    }

    public void setLocationId(java.lang.String locationId)
    {
        _locationId = locationId;
    }

    public java.lang.String getParentStorageLocation()
    {
        return _parentStorageLocation;
    }

    public void setParentStorageLocation(java.lang.String parentStorageLocation)
    {
        _parentStorageLocation = parentStorageLocation;
    }

    public java.lang.String getLocationName()
    {
        return _locationName;
    }

    public void setLocationName(java.lang.String locationName)
    {
        _locationName = locationName;
    }

    public boolean getIsparent()
    {
        return _isparent;
    }

    public void setIsparent(boolean isparent)
    {
        _isparent = isparent;
    }

    public int getMaxItemsInBox()
    {
        return _maxItemsInBox;
    }

    public void setMaxItemsInBox(int maxItemsInBox)
    {
        _maxItemsInBox = maxItemsInBox;
    }


}
