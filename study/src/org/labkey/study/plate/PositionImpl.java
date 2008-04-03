package org.labkey.study.plate;

import org.labkey.api.study.Position;
import org.labkey.api.data.Container;

/**
 * User: brittp
 * Date: Oct 31, 2006
 * Time: 2:37:56 PM
 */
public class PositionImpl implements Position
{
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
}
