/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.api.assay.plate;

import org.apache.commons.lang3.CharUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * User: brittp
 * Date: Oct 31, 2006
 * Time: 2:37:56 PM
 */
public class PositionImpl implements Position
{
    public static final String[] ALPHABET = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P",
            "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "AA", "AB", "AC", "AD", "AE", "AF"};

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

    private int getRowChar(char rowChar, char minChar, char maxChar)
    {
        if (rowChar < minChar || rowChar > maxChar)
        {
            throw new IllegalArgumentException(String.format("The well row character must be between %s and %s but was %s", minChar, maxChar, rowChar));
        }
        return rowChar - 'A';
    }

    public PositionImpl(Container container, @NotNull String description)
    {
        description = description.toUpperCase();
        _container = container;

        String rowStr = description.substring(0, 1);
        if (description.length() >= 3)
        {
            if (CharUtils.isAsciiAlpha(description.charAt(1)))
                rowStr = description.substring(0, 2);
        }

        _row = switch (rowStr.length())
        {
            case 1 -> getRowChar(rowStr.charAt(0), 'A', 'Z');
            case 2 -> getRowChar(rowStr.charAt(1), 'A', 'F') + 26;
            default -> throw new IllegalArgumentException("Well row descriptions should be between one and two characters, but was: '" + rowStr + "'");
        };

        try
        {
            _column = Integer.parseInt(description.substring(rowStr.length()));
            if (_column > 48)
                throw new IllegalArgumentException("Columns greater than 48 are not currently supported.");
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

    @Override
    public int getColumn()
    {
        return _column;
    }

    // needed for DB serialization
    public int getCol()
    {
        return getColumn();
    }

    @Override
    public int getRow()
    {
        return _row;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Position pos)
            return pos.getRow() == getRow() && pos.getColumn() == getColumn();
        return false;
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

    @Override
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

    @Override
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

    public static final class TestCase
    {
        @Test
        public void testParsePositions() throws Exception
        {
            Container c = ContainerManager.getSharedContainer();
            // verify we can parse A1 through AF48
            for (int i=0; i < ALPHABET.length; i++)
            {
                for (int j=1; j <= 48; j++)
                {
                    String description = ALPHABET[i] + j;
                    Position position = new PositionImpl(c, description);

                    assertEquals(i, position.getRow());
                    assertEquals(j, position.getColumn());
                }
            }
            // test unsupported well labels
            testUnsupported(c, "AG1");
            testUnsupported(c, "zz9");
            testUnsupported(c, "A500");
            testUnsupported(c, "12A");
        }

        private void testUnsupported(Container c, String description)
        {
            try
            {
                new PositionImpl(c, description);
                fail(description + " should not be a supported well name.");
            }
            catch (IllegalArgumentException e) {}
        }
    }
}
