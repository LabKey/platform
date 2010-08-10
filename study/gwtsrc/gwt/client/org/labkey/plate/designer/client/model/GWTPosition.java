/*
 * Copyright (c) 2010 LabKey Corporation
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

package gwt.client.org.labkey.plate.designer.client.model;

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

    /**
     * Returns a boolean indicating whether this position is inside the grid defined by the two parameter positions.
     * Containment is inclusive of the rows/columns occupied by the positions themselves.
     * @param first The upper-left or lower-right position of the containment grid.
     * @param second The upper-left or lower-right position of the containment grid.
     * @return Boolean indicating whether this point is inside the grid defined by the two parameter positions.
     */
    public boolean inside(GWTPosition first, GWTPosition second)
    {
        GWTPosition upperLeft = new GWTPosition(Math.min(first.getRow(), second.getRow()),
                                                Math.min(first.getCol(), second.getCol()));
        GWTPosition lowerRight = new GWTPosition(Math.max(first.getRow(), second.getRow()),
                                                Math.max(first.getCol(), second.getCol()));
        return upperLeft.getRow() <= getRow() &&
                lowerRight.getRow() >= getRow() &&
                upperLeft.getCol() <= getCol() &&
                lowerRight.getCol() >= getCol();
    }
}
