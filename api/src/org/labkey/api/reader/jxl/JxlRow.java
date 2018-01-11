/*
 * Copyright (c) 2011-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.reader.jxl;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.Iterator;

/**
 * User: klum
 * Date: May 2, 2011
 * Time: 6:53:29 PM
 */
public class JxlRow implements Row
{
    private final jxl.Cell[] _cells;
    private final int _idx;
    private final Sheet _sheet;

    public JxlRow(jxl.Cell[] cells, int idx, Sheet sheet)
    {
        _cells = cells;
        _idx = idx;
        _sheet = sheet;
    }

    @Override
    public Cell createCell(int column)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Cell createCell(int column, int type)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removeCell(Cell cell)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setRowNum(int rowNum)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getRowNum()
    {
        return _idx;
    }

    @Override
    public Cell getCell(int cellnum)
    {
        if (cellnum >= 0 && cellnum < _cells.length)
            return new JxlCell(_cells[cellnum], cellnum, this);

        return null;
    }

    @Override
    public Cell getCell(int cellnum, MissingCellPolicy policy)
    {
        return getCell(cellnum);
    }

    @Override
    public short getFirstCellNum()
    {
        return 0;
    }

    @Override
    public short getLastCellNum()
    {
        return (short)_cells.length;
    }

    @Override
    public int getPhysicalNumberOfCells()
    {
        return getLastCellNum();
    }

    @Override
    public void setHeight(short height)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setZeroHeight(boolean zHeight)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getZeroHeight()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setHeightInPoints(float height)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getHeight()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public float getHeightInPoints()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Iterator<Cell> cellIterator()
    {
        return new JxlCellIterator();
    }

    @Override
    public Sheet getSheet()
    {
        return _sheet;
    }

    @Override
    public Iterator<Cell> iterator()
    {
        return new JxlCellIterator();
    }

    @Override
    public boolean isFormatted()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellStyle getRowStyle()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setRowStyle(CellStyle cellStyle)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Cell createCell(int i, CellType cellType)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getOutlineLevel()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    protected class JxlCellIterator implements Iterator<Cell>
    {
        private int _current;
        private int _last;

        public JxlCellIterator()
        {
            _last = _cells.length;
        }

        @Override
        public boolean hasNext()
        {
            return _current < _last;
        }

        @Override
        public Cell next()
        {
            return getCell(_current++);
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException("remove not supported for JxlCellIterator");
        }
    }
}
