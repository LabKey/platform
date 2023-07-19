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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.Container;
import org.labkey.api.data.Transient;
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

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateImpl extends PropertySetImpl implements Plate
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
    private boolean _isTemplate;

    private Map<WellGroup.Type, Map<String, WellGroupImpl>> _groups;
    private List<WellGroupImpl> _deletedGroups;

    private WellImpl[][] _wells;
    private int _runId;      // NO_RUNID means no run yet, well data comes from file, dilution data must be calculated
    private int _plateNumber;

    public PlateImpl()
    {
        // no-param constructor for reflection
        _wells = null;
        _runId = PlateService.NO_RUNID;
        _plateNumber = 1;
    }

    public PlateImpl(Container container, String name, String type, int rowCount, int colCount)
    {
        super(container);
        _name = name;
        _type = type;
        _rows = rowCount;
        _columns = colCount;
        _container = container;
        _dataFileId = GUID.makeGUID();
    }

    public PlateImpl(PlateImpl template, double[][] wellValues, boolean[][] excluded, int runId, int plateNumber)
    {
        this(template.getContainer(), template.getName(), template.getType(), template.getRows(), template.getColumns());

        if (wellValues == null)
            wellValues = new double[template.getRows()][template.getColumns()];
        else if (wellValues.length != template.getRows() && wellValues[0].length != template.getColumns())
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

        for (WellGroup groupTemplate : template.getWellGroupTemplates(null))
            addWellGroup(new WellGroupImpl(this, (WellGroupImpl) groupTemplate));
        setContainer(template.getContainer());
    }

    @JsonIgnore
    @Override
    public @Nullable ActionURL detailsURL()
    {
        return new ActionURL(PlateController.DesignerAction.class, getContainer())
                .addParameter("templateName", getName())
                .addParameter("plateId", getRowId());
    }

    @JsonIgnore
    @Override
    public @Nullable QueryRowReference getQueryRowReference()
    {
        return null;
    }

    @JsonIgnore
    @Transient
    @Override
    public String getLSIDNamespacePrefix()
    {
        return Plate.super.getLSIDNamespacePrefix();
    }

    @Override
    public WellGroup addWellGroup(String name, WellGroup.Type type, Position upperLeft, Position lowerRight)
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

    @JsonIgnore
    @Override
    public WellGroupImpl addWellGroup(String name, WellGroup.Type type, List<Position> positions)
    {
        return storeWellGroup(createWellGroup(name, type, positions));
    }

    public WellGroup addWellGroup(WellGroupImpl template)
    {
        return storeWellGroup(template);
    }

    @JsonIgnore
    protected WellGroupImpl storeWellGroup(WellGroupImpl template)
    {
        template.setPlate(this);

        if (_groups == null)
            _groups = new HashMap<>();
        Map<String, WellGroupImpl> templatesByType = _groups.computeIfAbsent(template.getType(), k -> new LinkedHashMap<>());
        templatesByType.put(template.getName(), template);
        if (!wellGroupsInOrder(templatesByType))
        {
            List<WellGroupImpl> sortedWellGroups = new ArrayList<>(templatesByType.values());
            sortedWellGroups.sort(new WellGroupComparator());
            templatesByType.clear();
            for (WellGroupImpl wellGroup : sortedWellGroups)
                templatesByType.put(wellGroup.getName(), wellGroup);
        }
        if (!wellGroupsInOrder(templatesByType))
            throw new IllegalArgumentException("WellGroupTemplates are out of order.");
        return template;
    }

    private boolean wellGroupsInOrder(Map<String, WellGroupImpl> templates)
    {
        int row = -1;
        int col = -1;
        for (String name : templates.keySet())
        {
            WellGroupImpl template = templates.get(name);
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

    @JsonIgnore
    @Override
    public List<WellGroup> getWellGroups(Position position)
    {
        List<WellGroup> groups = new ArrayList<>();
        for (WellGroup template : getWellGroupTemplates(null))
        {
            if (template.contains(position))
                groups.add(template);
        }
        return groups;
    }

    @JsonIgnore
    @Override
    public List<WellGroup> getWellGroups()
    {
        return getWellGroupTemplates(null);
    }

    @JsonIgnore
    @Override
    public @Nullable WellGroup getWellGroup(int rowId)
    {
        return getWellGroupTemplates(null)
                .stream()
                .filter(wg -> wg.getRowId() != null && wg.getRowId() == rowId)
                .findFirst()
                .orElse(null);
    }

    @JsonIgnore
    @Override
    public List<WellGroup> getWellGroups(WellGroup.Type type)
    {
        return getWellGroupTemplates(type);
    }

    @JsonIgnore
    @Nullable
    public WellGroup getWellGroupTemplate(WellGroup.Type type, String name)
    {
        if (_groups == null)
            return null;
        Map<String, WellGroupImpl> typedGroups = _groups.get(type);
        if (typedGroups == null)
            return null;
        return typedGroups.get(name);
    }

    @JsonIgnore
    @NotNull
    public List<WellGroup> getWellGroupTemplates(@Nullable WellGroup.Type type)
    {
        List<WellGroup> allGroupTemplates = new ArrayList<>();
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
                for (Map<String, WellGroupImpl> typedGroupTemplates : _groups.values())
                    allGroupTemplates.addAll(typedGroupTemplates.values());
            }
        }
        return allGroupTemplates;
    }

    @JsonIgnore
    @Override
    public Map<WellGroup.Type, Map<String, WellGroup>> getWellGroupTemplateMap()
    {
        Map<WellGroup.Type, Map<String, WellGroup>> wellgroupTypeMap = new HashMap<>();
        if (_groups != null)
        {
            for (Map.Entry<WellGroup.Type, Map<String, WellGroupImpl>> templateEntry : _groups.entrySet())
            {
                Map<String, WellGroup> templateMap = new HashMap<>(templateEntry.getValue());
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

    @JsonIgnore
    public PositionImpl getPosition(int row, int col)
    {
        return new PositionImpl(_container, row, col);
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

    @JsonIgnore
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

    @JsonIgnore
    @Override
    public int getWellGroupCount()
    {
        int size = 0;
        if (_groups != null)
        {
            for (Map.Entry<WellGroup.Type, Map<String, WellGroupImpl>> entry : _groups.entrySet())
                size += entry.getValue().size();
        }
        return size;
    }

    @JsonIgnore
    @Override
    public int getWellGroupCount(WellGroup.Type type)
    {
        if (_groups != null)
        {
            Map<String, WellGroupImpl> typeMatch = _groups.get(type);
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

    @JsonIgnore
    public void markWellGroupForDeletion(WellGroup group)
    {
        WellGroupImpl wellGroup = (WellGroupImpl) group;
        if (wellGroup.getRowId() == null || wellGroup.getRowId() <= 0)
            throw new IllegalArgumentException();

        WellGroup existing = getWellGroup(wellGroup.getRowId());
        if (existing == null)
            throw new IllegalArgumentException("WellGroup doesn't exist on plate: " + wellGroup.getRowId());

        Map<String, WellGroupImpl> groupsForType = _groups.get(existing.getType());
        if (groupsForType != null)
            groupsForType.remove(existing.getName());

        if (_deletedGroups == null)
            _deletedGroups = new ArrayList<>();

        wellGroup.delete();
        _deletedGroups.add(wellGroup);
    }

    @JsonIgnore
    @NotNull
    public List<WellGroupImpl> getDeletedWellGroups()
    {
        return _deletedGroups == null ? Collections.emptyList() : Collections.unmodifiableList(_deletedGroups);
    }

    @JsonIgnore
    @Override
    public WellImpl getWell(int row, int col)
    {
        if (_wells != null)
            return _wells[row][col];
        else
        {
            // there is no data associated with this plate, return a well will no data.
            return new WellImpl(this, row, col, null, false);
        }
    }

    @JsonIgnore
    @Override
    public WellGroup getWellGroup(WellGroup.Type type, String wellGroupName)
    {
        WellGroup groupTemplate = getWellGroupTemplate(type, wellGroupName);
        if (groupTemplate == null)
            return null;
        return (WellGroupImpl) groupTemplate;
    }

    @JsonIgnore
    protected WellGroupImpl createWellGroup(String name, WellGroup.Type type, List<Position> positions)
    {
        return new WellGroupImpl(this, name, type, positions);
    }

    @JsonIgnore
    public void setWells(WellImpl[][] wells)
    {
        _wells = wells;
    }

    @JsonIgnore
    public WellImpl[][] getWells()
    {
        return _wells;
    }

    @Override
    public boolean isTemplate()
    {
        return _isTemplate;
    }

    public void setTemplate(boolean template)
    {
        _isTemplate = template;
    }

    @JsonIgnore
    public int getRunId()
    {
        return _runId;
    }

    public boolean mustCalculateStats()
    {
        return _runId == PlateService.NO_RUNID;
    }

    @JsonIgnore
    @Override
    public int getPlateNumber()
    {
        return _plateNumber;
    }
}
