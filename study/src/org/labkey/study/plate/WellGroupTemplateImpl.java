/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
        List<? extends Position> sortedPositions = new ArrayList<>(positions);
        sortedPositions.sort((Comparator<Position>) (first, second) ->
        {
            int comp = first.getColumn() - second.getColumn();
            if (comp == 0)
                comp = first.getRow() - second.getRow();
            return comp;
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

    @Override
    public String getPositionDescription()
    {
        if (_positions == null || _positions.size() == 0)
            return "";
        if (_positions.size() == 1)
            return _positions.get(0).getDescription();
        return _positions.get(0).getDescription() + "-" + _positions.get(_positions.size() - 1).getDescription();
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
