package org.labkey.study.plate;

import org.labkey.api.study.*;

import java.util.*;

/**
 * Copyright (C) 2004 Fred Hutchinson Cancer Research Center. All Rights Reserved.
 * User: migra
 * Date: Feb 10, 2006
 * Time: 1:57:01 PM
 */
public class PlateImpl extends PlateTemplateImpl implements Plate
{
    //UNDONE: Really just array of ints/values in this case, but
    //may be extended in the future
    private WellImpl[][] _wells;

    public PlateImpl()
    {
        // no-param constructor for reflection
    }

    public PlateImpl(PlateTemplateImpl template, double[][] wellValues)
    {
        super(template.getContainer(), template.getName(), template.getType(), template.getRows(), template.getColumns());
        _wells = new WellImpl[template.getRows()][template.getColumns()];
        for (int row = 0; row < template.getRows(); row++)
        {
            for (int col = 0; col < template.getColumns(); col++)
                _wells[row][col] = new WellImpl(this, row, col, wellValues[row][col]);
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
        List<WellGroup> groups = new ArrayList<WellGroup>();
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
}
