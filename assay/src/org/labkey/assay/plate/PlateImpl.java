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
import org.labkey.api.assay.plate.PlateCustomField;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.Container;
import org.labkey.api.data.Transient;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.util.GUID;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.assay.PlateController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateImpl extends PropertySetImpl implements Plate, Cloneable
{
    private String _name;
    private Integer _rowId;
    private int _createdBy;
    private long _created;
    private int _modifiedBy;
    private long _modified;
    private String _dataFileId;
    private String _assayType;
    private boolean _isTemplate;
    private Integer _plateSetId;
    private Integer _plateType;
    private String _description;

    private Map<WellGroup.Type, Map<String, WellGroupImpl>> _groups;
    private List<WellGroupImpl> _deletedGroups;

    private WellImpl[][] _wells;
    private Map<Integer, Well> _wellMap;

    private int _runId;      // NO_RUNID means no run yet, well data comes from file, dilution data must be calculated
    private int _plateNumber;
    private List<PlateCustomField> _customFields = Collections.emptyList();
    private Integer _metadataDomainId;

    private Integer _runCount;

    public PlateImpl()
    {
        // no-param constructor for reflection
        _wells = null;
        _runId = PlateService.NO_RUNID;
        _plateNumber = 1;
    }

    public PlateImpl(Container container, String name, String assayType, @NotNull PlateType plateType)
    {
        super(container);
        _name = name;
        _assayType = assayType;
        _container = container;
        _dataFileId = GUID.makeGUID();
        _plateType = plateType.getRowId();
    }

    public PlateImpl(PlateImpl plate, double[][] wellValues, boolean[][] excluded, int runId, int plateNumber)
    {
        this(plate.getContainer(), plate.getName(), plate.getAssayType(), plate.getPlateTypeObject());

        if (wellValues == null)
            wellValues = new double[plate.getRows()][plate.getColumns()];
        else if (wellValues.length != plate.getRows() && wellValues[0].length != plate.getColumns())
            throw new IllegalArgumentException("Well values array size must match the plate size");

        if (excluded != null && (excluded.length != plate.getRows() && excluded[0].length != plate.getColumns()))
            throw new IllegalArgumentException("Excluded values array size must match the plate size");

        _wells = new WellImpl[plate.getRows()][plate.getColumns()];
        _runId = runId;
        _plateNumber = plateNumber;
        for (int row = 0; row < plate.getRows(); row++)
        {
            for (int col = 0; col < plate.getColumns(); col++)
                _wells[row][col] = new WellImpl(this, row, col, wellValues[row][col], excluded != null && excluded[row][col]);
        }
        for (Map.Entry<String, Object> entry : plate.getProperties().entrySet())
            setProperty(entry.getKey(), entry.getValue());

        for (WellGroup group : plate.getWellGroups())
            addWellGroup(new WellGroupImpl(this, (WellGroupImpl) group));

        setContainer(plate.getContainer());
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

    public WellGroup addWellGroup(WellGroupImpl group)
    {
        return storeWellGroup(group);
    }

    @JsonIgnore
    protected WellGroupImpl storeWellGroup(WellGroupImpl group)
    {
        group.setPlate(this);

        if (_groups == null)
            _groups = new HashMap<>();
        Map<String, WellGroupImpl> groupsByType = _groups.computeIfAbsent(group.getType(), k -> new LinkedHashMap<>());
        groupsByType.put(group.getName(), group);
        if (!wellGroupsInOrder(groupsByType))
        {
            List<WellGroupImpl> sortedWellGroups = new ArrayList<>(groupsByType.values());
            sortedWellGroups.sort(new WellGroupComparator());
            groupsByType.clear();
            for (WellGroupImpl wellGroup : sortedWellGroups)
                groupsByType.put(wellGroup.getName(), wellGroup);
        }
        if (!wellGroupsInOrder(groupsByType))
            throw new IllegalArgumentException("WellGroups are out of order.");
        return group;
    }

    private boolean wellGroupsInOrder(Map<String, WellGroupImpl> groups)
    {
        int row = -1;
        int col = -1;
        for (String name : groups.keySet())
        {
            WellGroupImpl group = groups.get(name);
            Position topLeft = group.getTopLeft();
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
        for (WellGroup group : getWellGroups())
        {
            if (group.contains(position))
                groups.add(group);
        }
        return groups;
    }

    @JsonIgnore
    @Override
    public List<WellGroup> getWellGroups()
    {
        List<WellGroup> allGroups = new ArrayList<>();
        if (_groups != null)
        {
            for (Map<String, WellGroupImpl> groups : _groups.values())
                allGroups.addAll(groups.values());
        }
        return allGroups;
    }

    @JsonIgnore
    @Override
    public @Nullable WellGroup getWellGroup(int rowId)
    {
        return getWellGroups()
                .stream()
                .filter(wg -> wg.getRowId() != null && wg.getRowId() == rowId)
                .findFirst()
                .orElse(null);
    }

    @JsonIgnore
    @Override
    public List<WellGroup> getWellGroups(WellGroup.Type type)
    {
        List<WellGroup> allGroups = new ArrayList<>();
        if (_groups != null)
        {
            var typedGroups = _groups.get(type);
            if (typedGroups != null && !typedGroups.isEmpty())
                allGroups.addAll(typedGroups.values());
        }
        return allGroups;
    }

    @JsonIgnore
    @Override
    public Map<WellGroup.Type, Map<String, WellGroup>> getWellGroupMap()
    {
        Map<WellGroup.Type, Map<String, WellGroup>> wellgroupTypeMap = new HashMap<>();
        if (_groups != null)
        {
            for (Map.Entry<WellGroup.Type, Map<String, WellGroupImpl>> groupEntry : _groups.entrySet())
            {
                Map<String, WellGroup> groupMap = new HashMap<>(groupEntry.getValue());
                wellgroupTypeMap.put(groupEntry.getKey(), groupMap);
            }
        }
        return wellgroupTypeMap;
    }

    @Override
    @JsonIgnore
    public int getColumns()
    {
        return getPlateTypeObject().getColumns();
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    @JsonIgnore
    public int getRows()
    {
        return getPlateTypeObject().getRows();
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
    public String getAssayType()
    {
        return _assayType;
    }

    public void setAssayType(String type)
    {
        _assayType = type;
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

    @Override
    @Nullable
    public Well getWell(int rowId)
    {
        return _wellMap != null ? _wellMap.get(rowId) : null;
    }

    @JsonIgnore
    @Override
    public WellGroup getWellGroup(WellGroup.Type type, String wellGroupName)
    {
        if (_groups == null)
            return null;
        Map<String, WellGroupImpl> typedGroups = _groups.get(type);
        if (typedGroups == null)
            return null;
        return typedGroups.get(wellGroupName);
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

        // create a rowId to well map
        _wellMap = new HashMap<>();
        Arrays.stream(_wells).forEach(w -> {
            Arrays.stream(w).forEach(well -> {
                if (well != null)
                    _wellMap.put(well.getRowId(), well);
            });
        });
    }

    @JsonIgnore
    @Override
    public List<Well> getWells()
    {
        if (_wellMap != null)
            return _wellMap.values().stream().toList();
        else
            return Collections.emptyList();
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

    public void setPlateSet(Integer plateSetId)
    {
        _plateSetId = plateSetId;
    }

    public Integer getPlateSet()
    {
        return _plateSetId;
    }

    public void setPlateType(Integer plateType)
    {
        _plateType = plateType;
    }

    public Integer getPlateType()
    {
        return _plateType;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    @Override
    @JsonIgnore
    public @NotNull PlateType getPlateTypeObject()
    {
        return PlateManager.get().getPlateType(_plateType);
    }

    @Override
    @JsonIgnore
    public @Nullable PlateSet getPlateSetObject()
    {
        return PlateManager.get().getPlateSet(getContainer(), getPlateSet());
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

    @Override
    public @NotNull List<PlateCustomField> getCustomFields()
    {
        return _customFields;
    }

    public void setCustomFields(List<PlateCustomField> customFields)
    {
        _customFields = customFields;
    }

    public PlateImpl copy()
    {
        try
        {
            return (PlateImpl)super.clone();
        }
        catch (CloneNotSupportedException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    @Nullable
    @Override
    public Integer getMetadataDomainId()
    {
        return _metadataDomainId;
    }

    public void setMetadataDomainId(Integer metadataDomainId)
    {
        _metadataDomainId = metadataDomainId;
    }

    @Nullable
    @Override
    public Integer getRunCount()
    {
        return _runCount;
    }

    public void setRunCount(Integer runCount)
    {
        _runCount = runCount;
    }
}
