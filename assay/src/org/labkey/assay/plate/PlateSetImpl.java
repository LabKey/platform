/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateSetType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Entity;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.view.ActionURL;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.PlateSetTable;
import org.labkey.assay.query.AssayDbSchema;

import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateSetImpl extends Entity implements PlateSet
{
    private boolean _archived;
    private String _description;
    private String _name;
    private String _plateSetId;
    private transient Long _parentPlateSetId;
    private Long _primaryPlateSetId;
    private Long _rootPlateSetId;
    private Long _rowId;
    private boolean _template;
    private PlateSetType _type;
    private String _lsid;

    @Override
    public Long getRowId()
    {
        return _rowId;
    }

    public void setRowId(Long rowId)
    {
        _rowId = rowId;
    }

    public void setContainer(Container container)
    {
        containerId = container.getEntityId();
    }

    @JsonIgnore
    @Override
    public Container getContainer()
    {
        return ContainerManager.getForId(containerId);
    }

    @JsonIgnore
    public Container getFolder()
    {
        return getContainer();
    }

    // FieldKey for "Container" is overridden in PlateSetTable as "Folder"
    // This is necessary for deserialization from the database
    public void setFolder(Container container)
    {
        setContainer(container);
    }

    @SuppressWarnings("unused") // Serialized to the client
    public String getContainerName()
    {
        Container container = getContainer();
        return container == null ? null : container.getName();
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    @Override
    public String getLSID()
    {
        return _lsid;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    @Override
    public String getPlateSetId()
    {
        return _plateSetId;
    }

    public void setPlateSetId(String plateSetId)
    {
        _plateSetId = plateSetId;
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
    public boolean isAssay()
    {
        return PlateSetType.assay.equals(getType());
    }

    @Override
    @JsonIgnore
    public boolean isPrimary()
    {
        return PlateSetType.primary.equals(getType());
    }

    @Override
    @JsonIgnore
    public boolean isStandalone()
    {
        return getRootPlateSetId() == null && isAssay() && !isTemplate();
    }

    @Override
    public List<? extends Plate> getPlates()
    {
        if (isNew())
            return Collections.emptyList();

        return PlateCache.getPlatesForPlateSet(getContainer(), getRowId());
    }

    @JsonProperty("plateCount")
    public Integer getPlateCount()
    {
        if (isNew())
            return 0;

        TableInfo table = AssayDbSchema.getInstance().getTableInfoPlate();
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ")
                .append(table, "P")
                .append(" WHERE PlateSet = ?")
                .add(_rowId);

        return new SqlSelector(table.getSchema(), sql).getObject(Integer.class);
    }

    @JsonIgnore
    public boolean isFull()
    {
        return getPlateCount() >= MAX_PLATES;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @JsonIgnore
    @Override
    public @Nullable ActionURL detailsURL()
    {
        // Plate sets do not currently have their own page in LKS. Link to the default query row details.
        ActionURL url = QueryService.get().urlDefault(getContainer(), QueryAction.detailsQueryRow, PlateSchema.SCHEMA_NAME, PlateSetTable.NAME);
        url.addParameter(PlateSetTable.Column.RowId.name(), getRowId());
        return url;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    @JsonIgnore
    public Long getParentPlateSetId()
    {
        return _parentPlateSetId;
    }

    @JsonIgnore
    public void setParentPlateSetId(Long parentPlateSetId)
    {
        _parentPlateSetId = parentPlateSetId;
    }

    public Long getPrimaryPlateSetId()
    {
        return _primaryPlateSetId;
    }

    public void setPrimaryPlateSetId(Long primaryPlateSetId)
    {
        _primaryPlateSetId = primaryPlateSetId;
    }

    @Override
    public Long getRootPlateSetId()
    {
        return _rootPlateSetId;
    }

    public void setRootPlateSetId(Long rootPlateSetId)
    {
        _rootPlateSetId = rootPlateSetId;
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
    public PlateSetType getType()
    {
        return _type;
    }

    public void setType(PlateSetType type)
    {
        _type = type;
    }

    @JsonIgnore
    public int availablePlateCount()
    {
        return isNew() ? MAX_PLATES : Math.max(0, MAX_PLATES - getPlateCount());
    }

    @JsonIgnore
    public boolean isNew()
    {
        return _rowId == null || _rowId == 0;
    }
}
