package org.labkey.assay.plate;

import org.labkey.api.assay.plate.Position;

import java.util.Comparator;

public class WellGroupComparator implements Comparator<WellGroupImpl>
{
    @Override
    public int compare(WellGroupImpl first, WellGroupImpl second)
    {
        Position firstPos = first.getTopLeft();
        Position secondPos = second.getTopLeft();
        if (firstPos == null && secondPos == null)
            return 0;
        if (firstPos == null)
            return -1;
        if (secondPos == null)
            return 1;
        int comp = firstPos.getColumn() - secondPos.getColumn();
        if (comp == 0)
            comp = firstPos.getRow() - secondPos.getRow();
        return comp;
    }
}
