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
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Test;
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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Transient;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.assay.PlateController;
import org.labkey.assay.plate.model.PlateBean;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.PlateTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateImpl extends PropertySetImpl implements Plate, Cloneable
{
    private boolean _archived;
    private String _assayType = TsvPlateLayoutHandler.TYPE;
    private String _barcode;
    private long _created;
    private User _createdBy;
    private List<PlateCustomField> _customFields = Collections.emptyList();
    private String _dataFileId;
    private List<WellGroupImpl> _deletedGroups;
    private String _description;
    private Map<WellGroup.Type, Map<String, WellGroupImpl>> _groups;
    private long _modified;
    private User _modifiedBy;
    private String _name;
    private String _plateId;
    private int _plateNumber = 1;
    private PlateSet _plateSet;
    private PlateType _plateType;
    private Integer _rowId;
    private Integer _runCount;
    private int _runId = PlateService.NO_RUNID; // NO_RUNID means no run yet, well data comes from file, dilution data must be calculated
    private boolean _template;
    private WellImpl[][] _wells = null;
    private Map<Integer, Well> _wellMap;
    private Integer _metadataDomainId;

    // no-param constructor for reflection
    public PlateImpl()
    {
    }

    public PlateImpl(Container container, String name, @Nullable String barcode, String assayType, @NotNull PlateType plateType)
    {
        super(container);
        _name = StringUtils.trimToNull(name);
        _barcode = StringUtils.trimToNull(barcode);
        if (StringUtils.trimToNull(assayType) != null)
            _assayType = assayType;
        _dataFileId = GUID.makeGUID();
        _plateType = plateType;
    }

    public PlateImpl(Container container, String name, @Nullable String barcode, @NotNull PlateType plateType)
    {
        this(container, name, barcode, null, plateType);
    }

    // Note that barcode values will be auto-generated
    public PlateImpl(@NotNull PlateImpl plate, double[][] wellValues, boolean[][] excluded, int runId, int plateNumber)
    {
        this(plate.getContainer(), plate.getName(), null, plate.getAssayType(), plate.getPlateType());

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
    }

    public static PlateImpl from(PlateBean bean)
    {
        PlateImpl plate = new PlateImpl();

        // plate fields
        plate.setRowId(bean.getRowId());
        plate.setLsid(bean.getLsid());
        plate.setName(bean.getName());
        plate.setBarcode(bean.getBarcode());
        plate.setTemplate(bean.getTemplate());
        plate.setDataFileId(bean.getDataFileId());
        plate.setAssayType(bean.getAssayType());
        plate.setPlateId(bean.getPlateId());
        plate.setArchived(bean.getArchived());
        plate.setDescription(bean.getDescription());

        // entity fields
        Container container = ContainerManager.getForId(bean.getContainerId());
        plate.setContainer(container);
        plate.setCreated(bean.getCreated());
        plate.setCreatedBy(UserManager.getUser(bean.getCreatedBy()));
        plate.setModified(bean.getModified());
        plate.setModifiedBy(UserManager.getUser(bean.getModifiedBy()));

        // plate type and plate set objects
        PlateType plateType = PlateManager.get().getPlateType(bean.getPlateType());
        if (plateType == null)
            throw new IllegalStateException("Unable to get Plate Type with id : " + bean.getPlateType());
        plate.setPlateType(plateType);

        PlateSet plateSet = PlateManager.get().getPlateSet(container, bean.getPlateSet());
        if (plateSet == null)
            throw new IllegalStateException("Unable to get Plate Set with id : " + bean.getPlateSet());
        plate.setPlateSet(plateSet);

        return plate;
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
        if (isNew())
            return null;
        return new QueryRowReference(getContainer(), SchemaKey.fromParts(PlateSchema.SCHEMA_NAME), PlateTable.NAME, FieldKey.fromParts("rowId"), getRowId());
    }

    @JsonIgnore
    @Transient
    @Override
    public String getLSIDNamespacePrefix()
    {
        return Plate.super.getLSIDNamespacePrefix();
    }

    @Override
    public @NotNull WellGroup addWellGroup(String name, WellGroup.Type type, Position upperLeft, Position lowerRight)
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
    public @NotNull WellGroupImpl addWellGroup(String name, WellGroup.Type type, List<Position> positions)
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
    public @NotNull List<WellGroup> getWellGroups(Position position)
    {
        List<WellGroup> wellGroups = getWellGroups();
        if (wellGroups.isEmpty())
            return Collections.emptyList();

        List<WellGroup> groups = new ArrayList<>();
        for (WellGroup group : wellGroups)
        {
            if (group.contains(position))
                groups.add(group);
        }

        return groups;
    }

    @JsonIgnore
    @Override
    public @NotNull List<WellGroup> getWellGroups()
    {
        if (_groups == null)
            return Collections.emptyList();

        List<WellGroup> allGroups = new ArrayList<>();
        for (Map<String, WellGroupImpl> groups : _groups.values())
            allGroups.addAll(groups.values());

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
    public @NotNull List<WellGroup> getWellGroups(WellGroup.Type type)
    {
        if (_groups == null)
            return Collections.emptyList();

        List<WellGroup> allGroups = new ArrayList<>();
        var typedGroups = _groups.get(type);
        if (typedGroups != null && !typedGroups.isEmpty())
            allGroups.addAll(typedGroups.values());

        return allGroups;
    }

    @JsonIgnore
    @Override
    public @NotNull Map<WellGroup.Type, Map<String, WellGroup>> getWellGroupMap()
    {
        if (_groups == null)
            return Collections.emptyMap();

        Map<WellGroup.Type, Map<String, WellGroup>> wellgroupTypeMap = new HashMap<>();
        for (Map.Entry<WellGroup.Type, Map<String, WellGroupImpl>> groupEntry : _groups.entrySet())
        {
            Map<String, WellGroup> groupMap = new HashMap<>(groupEntry.getValue());
            wellgroupTypeMap.put(groupEntry.getKey(), groupMap);
        }

        return wellgroupTypeMap;
    }

    @Override
    public boolean isArchived()
    {
        return _archived;
    }

    public void setArchived(boolean archived)
    {
        _archived = archived;
    }

    @Override
    @JsonIgnore
    public int getColumns()
    {
        if (_plateType == null)
            return 0;
        return _plateType.getColumns();
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
        if (_plateType == null)
            return 0;
        return _plateType.getRows();
    }

    @JsonIgnore
    @Override
    public @NotNull PositionImpl getPosition(int row, int col)
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

    @Override
    public String getBarcode()
    {
        return _barcode;
    }

    @Override
    public void setBarcode(String barcode)
    {
        _barcode = barcode;
    }

    public Date getCreated()
    {
        return new Date(_created);
    }

    public void setCreated(Date created)
    {
        _created = created.getTime();
    }

    @JsonProperty("createdBy")
    public JSONObject getCreatedBy()
    {
        if (_createdBy == null)
            return null;
        return _createdBy.getUserProps();
    }

    public void setCreatedBy(User createdBy)
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

    @JsonProperty("modifiedBy")
    public JSONObject getModifiedBy()
    {
        if (_modifiedBy == null)
            return null;
        return _modifiedBy.getUserProps();
    }

    public void setModifiedBy(User modifiedBy)
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
    public @NotNull WellImpl getWell(int row, int col)
    {
        if (_wells != null)
            return _wells[row][col];

        // there is no data associated with this plate, return a well will no data.
        return new WellImpl(this, row, col, null, false);
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
    public @NotNull List<Well> getWells()
    {
        if (_wellMap != null)
            return _wellMap.values().stream().toList();
        else if (_wells != null)
        {
            List<Well> wells = new ArrayList<>();
            for (int row = 0; row < getRows(); row++)
                for (int col = 0; col < getColumns(); col++)
                    wells.add(getWell(row, col));
            return wells;
        }

        return Collections.emptyList();
    }

    @Override
    public boolean isTemplate()
    {
        return _template;
    }

    public void setTemplate(boolean template)
    {
        _template = template;
    }

    @Override
    @JsonIgnore
    public @Nullable PlateSet getPlateSet()
    {
        return _plateSet;
    }

    public void setPlateSet(PlateSet plateSet)
    {
        _plateSet = plateSet;
    }

    @JsonProperty("plateSet")
    public Integer getPlateSetId()
    {
        return _plateSet.getRowId();
    }

    @Override
    public @NotNull PlateType getPlateType()
    {
        return _plateType;
    }

    public void setPlateType(PlateType plateType)
    {
        _plateType = plateType;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
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

    @Override
    public PlateImpl copy()
    {
        try
        {
            return (PlateImpl) super.clone();
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

    @NotNull
    @Override
    public String getPlateId()
    {
        return _plateId;
    }

    public void setPlateId(String plateId)
    {
        _plateId = plateId;
    }

    @Override
    public boolean isIdentifierMatch(String id)
    {
        return id != null && !id.isEmpty() && (id.equals(getRowId() + "") || id.equalsIgnoreCase(getPlateId()) || id.equalsIgnoreCase(getName()));
    }

    @Override
    public boolean isNew()
    {
        return _rowId == null || _rowId <= 0;
    }

    public static final class TestCase
    {
        @Test
        public void testIdentifierMatch()
        {
            PlateImpl plate = new PlateImpl();
            plate.setRowId(1);
            plate.setPlateId("test-id");
            plate.setName("Test Name");
            
            assertTrue("Expected plate to be accessible via rowId", plate.isIdentifierMatch("1"));
            assertFalse("Expected plate to not match invalid rowId", plate.isIdentifierMatch("2"));

            assertTrue("Expected plate to be accessible via plateId", plate.isIdentifierMatch("test-id"));
            assertTrue("Expected plate to be accessible via plateId", plate.isIdentifierMatch("TEST-ID"));
            assertFalse("Expected plate to not match invalid plateId", plate.isIdentifierMatch("test id"));

            assertTrue("Expected plate to be accessible via name", plate.isIdentifierMatch("Test Name"));
            assertTrue("Expected plate to be accessible via name", plate.isIdentifierMatch("test name"));
            assertFalse("Expected plate to not match invalid name", plate.isIdentifierMatch("TestName"));
            assertFalse("Expected plate to not match invalid name", plate.isIdentifierMatch(""));
            assertFalse("Expected plate to not match invalid name", plate.isIdentifierMatch(null));
        }
    }
}
