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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.util.ResultSetUtil;

/**
 * Bean Class for for Box.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class Box
{
    private int _hashCode = 0;
    private int _boxId = 0;
    private int _freezerId = 0;

    private String _freezerIdDisplay = null;
    private java.lang.String _shelf = null;
    private java.lang.String _rack = null;
    private java.lang.String _drawer = null;
    private java.lang.String _boxName = null;
    private int _maxItemsInBox = 0;
    private java.lang.String _container = null;

    public Box()
    {
    }

    public int getBoxId()
    {
        return _boxId;
    }

    public void setBoxId(int boxId)
    {
        _boxId = boxId;
    }

    public int getFreezerId()
    {
        return _freezerId;
    }

    public void setFreezerId(int freezerId)
    {
        _freezerId = freezerId;
    }

    public java.lang.String getShelf()
    {
        return _shelf;
    }

    public void setShelf(java.lang.String shelf)
    {
        _shelf = shelf;
    }

    public java.lang.String getRack()
    {
        return _rack;
    }

    public void setRack(java.lang.String rack)
    {
        _rack = rack;
    }

    public java.lang.String getDrawer()
    {
        return _drawer;
    }

    public void setDrawer(java.lang.String drawer)
    {
        _drawer = drawer;
    }

    public java.lang.String getBoxName()
    {
        return _boxName;
    }

    public void setBoxName(java.lang.String boxName)
    {
        _boxName = boxName;
    }

    public int getMaxItemsInBox()
    {
        return _maxItemsInBox;
    }

    public void setMaxItemsInBox(int maxItemsInBox)
    {
        _maxItemsInBox = maxItemsInBox;
    }

    public java.lang.String getContainer()
    {
        return _container;
    }

    public void setContainer(java.lang.String container)
    {
        _container = container;
    }

    public boolean equals(Object o)
    {
        if (null == o || !(o instanceof Box))
            return false;

        Box box = (Box) o;
        return box._boxName.equals(_boxName) && box._container.equals(_container)
                && box._freezerId == _freezerId && box._shelf.equals(_shelf)
                && box._rack.equals(_rack) && box._drawer.equals(_drawer);

    }

    public int hashCode()
    {
        if (0 == _hashCode)
            _hashCode = (_boxName + _container + _freezerId + _shelf + _rack + _drawer).hashCode();

        return _hashCode;
    }


}
