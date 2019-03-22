/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.api.study;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.study.Position;
import org.labkey.api.data.Container;

/**
 * User: brittp
 * Date: Oct 31, 2006
 * Time: 2:37:56 PM
 */
public class PositionImpl implements Position
{
    public static final char[] ALPHABET = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};

    protected int _row;
    protected int _column;
    private Container _container;
    private Integer _rowId;
    private String _lsid;
    private Integer _plateId;

    public PositionImpl()
    {
        // no-arg constructor for reflection    
    }

    public PositionImpl(Container container, @NotNull String description)
    {
        _container = container;
        if (description.length() < 2)
        {
            throw new IllegalArgumentException("Well descriptions should be at least two characters, but was: '" + description + "'");
        }
        description = description.toUpperCase();
        char rowChar = description.charAt(0);
        if (rowChar < 'A' || rowChar > 'Z')
        {
            throw new IllegalArgumentException("Well descriptions should start with a letter, but was: '" + description + "'");
        }
        _row = rowChar - 'A';
        try
        {
            _column = Integer.parseInt(description.substring(1));
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("Could not parse column for description: '" + description + "'", e);
        }
    }

    public PositionImpl(Container container, int row, int column)
    {
        _container = container;
        _row = row;
        _column = column;
    }

    public int getColumn()
    {
        return _column;
    }

    public int getRow()
    {
        return _row;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof Position))
            return false;
        return ((Position) obj).getRow() == getRow() && ((Position) obj).getColumn() == getColumn();
    }

    @Override
    public int hashCode()
    {
        return (getRow() << 4 | getColumn());
    }

    @Override
    public String toString()
    {
        return "(" + getRow() + ", " + getColumn() + ")";
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public void setColumn(int column)
    {
        _column = column;
    }

    public void setRow(int row)
    {
        _row = row;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    // methods used to enable reflection in our table layer; because 'column' is a reserved word
    // under postgres, we have to name the column 'col'
    public void setCol(int column)
    {
        setColumn(column);
    }

    public int getCol()
    {
        return getColumn();
    }

    public Integer getPlateId()
    {
        return _plateId;
    }

    public void setPlateId(Integer plateId)
    {
        _plateId = plateId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    @Override
    public String getDescription()
    {
        if (getRow() > ALPHABET.length)
            return "Row " + (getRow() + 1) + ", Column " + (getColumn() + 1);
        return "" + ALPHABET[getRow()] + (getColumn() + 1);
    }
}
