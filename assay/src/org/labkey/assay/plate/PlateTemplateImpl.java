/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.PlateTemplate;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.assay.plate.WellGroupTemplate;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.assay.PlateController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 1:13:45 PM
 */
public class PlateTemplateImpl extends PropertySetImpl implements PlateTemplate
{
    private String _name;
    private Integer _rowId;
    private int _rows;
    private int _columns;
    private int _createdBy;
    private long _created;
    private int _modifiedBy;
    private long _modified;
    private String _dataFileId;
    private String _type;

    private Map<WellGroup.Type, Map<String, WellGroupTemplateImpl>> _groups;
    private List<WellGroupTemplateImpl> _deletedGroups;

    public PlateTemplateImpl()
    {
        // no-param constructor for reflection
    }

    public PlateTemplateImpl(Container container, String name, String type, int rowCount, int colCount)
    {
        super(container);
        _name = name;
        _type = type;
        _rows = rowCount;
        _columns = colCount;
        _container = container;
        _dataFileId = GUID.makeGUID();
    }

    @Override
    public @Nullable ActionURL detailsURL()
    {
        return new ActionURL(PlateController.DesignerAction.class, getContainer())
                .addParameter("templateName", getName())
                .addParameter("plateId", getRowId());
    }

    @Override
    public @Nullable QueryRowReference getQueryRowReference()
    {
        return null;
    }

    @Override
    public WellGroupTemplate addWellGroup(String name, WellGroup.Type type, Position upperLeft, Position lowerRight)
    {
        int regionWidth = lowerRight.getColumn() - upperLeft.getColumn() + 1;
        int regionHeight = lowerRight.getRow() - upperLeft.getRow();
        List<Position> allPositions = new ArrayList<>(regionWidth * regionHeight);
        for (int col = upperLeft.getColumn(); col <= lowerRight.getColumn(); col++)
        {
            for (int row = upperLeft.getRow(); row <= lowerRight.getRow(); row++)
                allPositions.add(new PositionImpl(_container, row, col));
        }
        return addWellGroup(name, type, allPositions);
    }

    @Override
    public WellGroupTemplateImpl addWellGroup(String name, WellGroup.Type type, List<Position> positions)
    {
        return storeWellGroup(createWellGroup(name, type, positions));
    }

    protected WellGroupTemplateImpl createWellGroup(String name, WellGroup.Type type, List<Position> positions)
    {
        return new WellGroupTemplateImpl(this, name, type, positions);
    }

    public WellGroupTemplate addWellGroup(WellGroupTemplateImpl template)
    {
        return storeWellGroup(template);
    }

    protected WellGroupTemplateImpl storeWellGroup(WellGroupTemplateImpl template)
    {
        if (_groups == null)
            _groups = new HashMap<>();
        Map<String, WellGroupTemplateImpl> templatesByType = _groups.computeIfAbsent(template.getType(), k -> new LinkedHashMap<>());
        templatesByType.put(template.getName(), template);
        if (!wellGroupsInOrder(templatesByType))
        {
            List<WellGroupTemplateImpl> sortedWellGroups = new ArrayList<>(templatesByType.values());
            sortedWellGroups.sort(new WellGroupTemplateComparator());
            templatesByType.clear();
            for (WellGroupTemplateImpl wellGroup : sortedWellGroups)
                templatesByType.put(wellGroup.getName(), wellGroup);
        }
        if (!wellGroupsInOrder(templatesByType))
            throw new IllegalArgumentException("WellGroupTemplates are out of order.");
        return template;
    }

    private boolean wellGroupsInOrder(Map<String, WellGroupTemplateImpl> templates)
    {
        int row = -1;
        int col = -1;
        for (String name : templates.keySet())
        {
            WellGroupTemplateImpl template = templates.get(name);
            Position topLeft = template.getTopLeft();
            if (topLeft != null)
            {
                if (col > topLeft.getColumn())
                    return false;
                if (col == topLeft.getColumn() && row > topLeft.getRow())
                    return false;
                row = topLeft.getRow();
                col = topLeft.getColumn();
            }
        }
        return true;
    }

    @Override
    public List<? extends WellGroupTemplateImpl> getWellGroups(Position position)
    {
        List<WellGroupTemplateImpl> groups = new ArrayList<>();
        for (WellGroupTemplateImpl template : getWellGroupTemplates(null))
        {
            if (template.contains(position))
                groups.add(template);
        }
        return groups;
    }

    @Override
    public List<? extends WellGroupTemplateImpl> getWellGroups()
    {
        return getWellGroupTemplates(null);
    }

    @Override
    public @Nullable WellGroupTemplateImpl getWellGroup(int rowId)
    {
        return getWellGroupTemplates(null)
                .stream()
                .filter(wg -> wg.getRowId() != null && wg.getRowId() == rowId)
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<? extends WellGroupTemplateImpl> getWellGroups(WellGroup.Type type)
    {
        return getWellGroupTemplates(type);
    }

    @Nullable
    public WellGroupTemplateImpl getWellGroupTemplate(WellGroup.Type type, String name)
    {
        if (_groups == null)
            return null;
        Map<String, WellGroupTemplateImpl> typedGroups = _groups.get(type);
        if (typedGroups == null)
            return null;
        return typedGroups.get(name);
    }

    @NotNull
    public List<? extends WellGroupTemplateImpl> getWellGroupTemplates(@Nullable WellGroup.Type type)
    {
        List<WellGroupTemplateImpl> allGroupTemplates = new ArrayList<>();
        if (_groups != null)
        {
            if (type != null)
            {
                var typedGroupTemplates = _groups.get(type);
                if (typedGroupTemplates != null && !typedGroupTemplates.isEmpty())
                    allGroupTemplates.addAll(typedGroupTemplates.values());
            }
            else
            {
                for (Map<String, WellGroupTemplateImpl> typedGroupTemplates : _groups.values())
                    allGroupTemplates.addAll(typedGroupTemplates.values());
            }
        }
        return allGroupTemplates;
    }

    @Override
    public Map<WellGroup.Type, Map<String, WellGroupTemplate>> getWellGroupTemplateMap()
    {
        Map<WellGroup.Type, Map<String, WellGroupTemplate>> wellgroupTypeMap = new HashMap<>();
        if (_groups != null)
        {
            for (Map.Entry<WellGroup.Type, Map<String, WellGroupTemplateImpl>> templateEntry : _groups.entrySet())
            {
                Map<String, WellGroupTemplate> templateMap = new HashMap<>(templateEntry.getValue());
                wellgroupTypeMap.put(templateEntry.getKey(), templateMap);
            }
        }
        return wellgroupTypeMap;
    }

    @Override
    public int getColumns()
    {
        return _columns;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public int getRows()
    {
        return _rows;
    }


    @Override
    public PositionImpl getPosition(int row, int col)
    {
        return new PositionImpl(_container, row, col);
    }


    public boolean isTemplate()
    {
        return true;
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

    public void setColumns(int columns)
    {
        _columns = columns;
    }

    public void setRows(int rows)
    {
        _rows = rows;
    }

    @Override
    public void setName(String name)
    {
        _name = name;
    }

    public Date getCreated()
    {
        return new Date(_created);
    }

    public void setCreated(Date created)
    {
        _created = created.getTime();
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getModified()
    {
        return new Date(_modified);
    }

    public void setModified(Date modified)
    {
        _modified = modified.getTime();
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public String getDataFileId()
    {
        return _dataFileId;
    }

    public String getEntityId()
    {
        return _dataFileId;
    }

    public void setDataFileId(String dataFileId)
    {
        _dataFileId = dataFileId;
    }

    public String getContainerId()
    {
        return _container.getId();
    }

    @Override
    public int getWellGroupCount()
    {
        int size = 0;
        if (_groups != null)
        {
            for (Map.Entry<WellGroup.Type, Map<String, WellGroupTemplateImpl>> entry : _groups.entrySet())
                size += entry.getValue().size();
        }
        return size;
    }

    @Override
    public int getWellGroupCount(WellGroup.Type type)
    {
        if (_groups != null)
        {
            Map<String, WellGroupTemplateImpl> typeMatch = _groups.get(type);
            if (typeMatch != null)
                return typeMatch.size();
        }
        return 0;
    }

    @Override
    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public void markWellGroupForDeletion(WellGroupTemplateImpl wellGroup)
    {
        if (wellGroup.getRowId() == null || wellGroup.getRowId() <= 0)
            throw new IllegalArgumentException();

        WellGroupTemplateImpl existing = getWellGroup(wellGroup.getRowId());
        if (existing == null)
            throw new IllegalArgumentException("WellGroup doesn't exist on plate: " + wellGroup.getRowId());

        Map<String, WellGroupTemplateImpl> groupsForType = _groups.get(existing.getType());
        if (groupsForType != null)
            groupsForType.remove(existing.getName());

        if (_deletedGroups == null)
            _deletedGroups = new ArrayList<>();

        wellGroup.delete();
        _deletedGroups.add(wellGroup);
    }

    @NotNull
    public List<? extends WellGroupTemplateImpl> getDeletedWellGroups()
    {
        return _deletedGroups == null ? Collections.emptyList() : Collections.unmodifiableList(_deletedGroups);
    }
}
