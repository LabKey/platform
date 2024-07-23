package org.labkey.assay.plate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateSetType;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.assay.query.AssayDbSchema;

import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateSetImpl extends Entity implements PlateSet
{
    private boolean _archived;
    private Container _container;
    private String _description;
    private String _name;
    private String _plateSetId;
    private Integer _primaryPlateSetId;
    private Integer _rootPlateSetId;
    private Integer _rowId;
    private boolean _template;
    private PlateSetType _type;

    @Override
    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    @JsonIgnore
    @Override
    public Container getContainer()
    {
        return _container;
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

    @Override
    public String getContainerId()
    {
        return _container == null ? null : _container.getId();
    }

    @Override
    public String getContainerPath()
    {
        return _container == null ? null : _container.getPath();
    }

    public String getContainerName()
    {
        return _container == null ? null : _container.getName();
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
    public List<Plate> getPlates()
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

    public void setDescription(String description)
    {
        _description = description;
    }

    public Integer getPrimaryPlateSetId()
    {
        return _primaryPlateSetId;
    }

    public void setPrimaryPlateSetId(Integer primaryPlateSetId)
    {
        _primaryPlateSetId = primaryPlateSetId;
    }

    public Integer getRootPlateSetId()
    {
        return _rootPlateSetId;
    }

    public void setRootPlateSetId(Integer rootPlateSetId)
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
