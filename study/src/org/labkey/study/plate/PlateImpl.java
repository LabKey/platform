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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.study.Plate;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.Position;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.WellGroupTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: migra
 * Date: Feb 10, 2006
 * Time: 1:57:01 PM
 */
public class PlateImpl extends PlateTemplateImpl implements Plate
{
    //UNDONE: Really just array of ints/values in this case, but
    //may be extended in the future
    private WellImpl[][] _wells;
    private final int _runId;      // NO_RUNID means no run yet, well data comes from file, dilution data must be calculated
    private final int _plateNumber;

    public PlateImpl()
    {
        // no-param constructor for reflection
        _wells = null;
        _runId = PlateService.NO_RUNID;
        _plateNumber = 1;
    }

    public PlateImpl(PlateTemplateImpl template, double[][] wellValues, @Nullable boolean[][] excluded, int runId, int plateNumber)
    {
        super(template.getContainer(), template.getName(), template.getType(), template.getRows(), template.getColumns());

        if (wellValues.length != template.getRows() && wellValues[0].length != template.getColumns())
            throw new IllegalArgumentException("Well values array size must match the template size");

        if (excluded != null && (excluded.length != template.getRows() && excluded[0].length != template.getColumns()))
            throw new IllegalArgumentException("Excluded values array size must match the template size");

        _wells = new WellImpl[template.getRows()][template.getColumns()];
        _runId = runId;
        _plateNumber = plateNumber;
        for (int row = 0; row < template.getRows(); row++)
        {
            for (int col = 0; col < template.getColumns(); col++)
                _wells[row][col] = new WellImpl(this, row, col, wellValues[row][col], excluded != null && excluded[row][col]);
        }
        for (Map.Entry<String, Object> entry : template.getProperties().entrySet())
            setProperty(entry.getKey(), entry.getValue());

        for (WellGroupTemplateImpl groupTemplate : template.getWellGroupTemplates())
            addWellGroup(new WellGroupImpl(this, groupTemplate));
        setContainer(template.getContainer());
    }


    public WellImpl getWell(int row, int col)
    {
        return _wells[row][col];
    }

    public WellGroup getWellGroup(WellGroup.Type type, String wellGroupName)
    {
        WellGroupTemplate groupTemplate = getWellGroupTemplate(type, wellGroupName);
        if (groupTemplate == null)
            return null;
        return (WellGroupImpl) groupTemplate;
    }

    public List<WellGroup> getWellGroups(WellGroup.Type type)
    {
        List<WellGroup> groups = new ArrayList<>();
        for (WellGroupTemplate entry : getWellGroupTemplates())
        {
            if (entry.getType() == type)
                groups.add((WellGroupImpl) entry);
        }
        return groups;
    }

    public List<WellGroupImpl> getWellGroups(Position position)
    {
        return (List<WellGroupImpl>) super.getWellGroups(position);
    }

    public List<WellGroupImpl> getWellGroups()
    {
        return (List<WellGroupImpl>) super.getWellGroups();
    }

    protected WellGroupTemplateImpl createWellGroup(String name, WellGroup.Type type, List<Position> positions)
    {
        return new WellGroupImpl(this, name, type, positions);
    }

    @Override
    protected WellGroupTemplate storeWellGroup(WellGroupTemplateImpl template)
    {
        ((WellGroupImpl) template).setPlate(this);
        return super.storeWellGroup(template);
    }

    @Override
    public WellImpl getPosition(int row, int col)
    {
        return _wells[row][col];
    }

    public void setWells(WellImpl[][] wells)
    {
        _wells = wells;
    }

    public WellImpl[][] getWells()
    {
        return _wells;
    }

    @Override
    public boolean isTemplate()
    {
        return false;
    }

    public int getRunId()
    {
        return _runId;
    }

    public boolean mustCalculateStats()
    {
        return _runId == PlateService.NO_RUNID;
    }

    public int getPlateNumber()
    {
        return _plateNumber;
    }
}
