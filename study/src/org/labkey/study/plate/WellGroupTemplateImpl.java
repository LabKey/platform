package org.labkey.study.plate;

import org.labkey.api.study.WellGroup;
import org.labkey.api.study.Position;
import org.labkey.api.study.WellGroupTemplate;

import java.util.*;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 4:32:11 PM
 */
public class WellGroupTemplateImpl extends PropertySetImpl implements WellGroupTemplate
{
    private Integer _rowId;
    private String _name;
    private WellGroup.Type _type;
    protected Integer _plateId;
    protected List<? extends Position> _positions;

    public WellGroupTemplateImpl()
    {
        // no-param constructor for reflection
    }

    public WellGroupTemplateImpl(PlateTemplateImpl owner, String name, WellGroup.Type type, List<? extends Position> positions)
    {
        super(owner.getContainer());
        _type = type;
        _name = name;
        _plateId = owner.getRowId() != null ? owner.getRowId() : null;
        _positions = sortPositions(positions);
    }

    private static List<? extends Position> sortPositions(List<? extends Position> positions)
    {
        List<? extends Position> sortedPositions = new ArrayList<Position>(positions);
        Collections.sort(sortedPositions, new Comparator<Position>()
        {
            public int compare(Position first, Position second)
            {
                int comp = first.getColumn() - second.getColumn();
                if (comp == 0)
                    comp = first.getRow() - second.getRow();
                return comp;
            }
        });
        return sortedPositions;
    }

    public List<Position> getPositions()
    {
        return Collections.unmodifiableList(_positions);
    }

    public WellGroup.Type getType()
    {
        return _type;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public boolean contains(Position position)
    {
        return _positions.contains(position);
    }
    
    public void setPositions(List<? extends Position> positions)
    {
        _positions = sortPositions(positions);
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public String getTypeName()
    {
        return _type.name();
    }

    public void setTypeName(String type)
    {
        _type = WellGroup.Type.valueOf(type);
    }
    
    public Integer getPlateId()
    {
        return _plateId;
    }

    public void setPlateId(Integer plateId)
    {
        _plateId = plateId;
    }

    public boolean isTemplate()
    {
        return true;
    }

    public Position getTopLeft()
    {
        if (_positions.isEmpty())
            return null;
        return _positions.get(0);
    }
}
