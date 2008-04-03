package org.labkey.plate.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * User: brittp
 * Date: Feb 2, 2007
 * Time: 3:04:10 PM
 */
public class GWTPosition implements IsSerializable
{
    private int _row;
    private int _col;

    public GWTPosition()
    {
        // empty constructor for serialization
    }

    public GWTPosition(int row, int col)
    {
        _row = row;
        _col = col;
    }

    public int getCol()
    {
        return _col;
    }

    public int getRow()
    {
        return _row;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof GWTPosition))
            return false;
        return ((GWTPosition) o).getRow() == getRow() &&
                ((GWTPosition) o).getCol() == getCol();
    }

    public int hashCode()
    {
        int result;
        result = _row;
        result = 31 * result + _col;
        return result;
    }
}
