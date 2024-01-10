package org.labkey.assay.plate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.Entity;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateSetImpl extends Entity implements PlateSet
{
    private Integer _rowId;
    private String _name;
    private boolean _archived;
    private Container _container;

    public PlateSetImpl()
    {
        // no-param constructor for reflection
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
    public boolean isArchived()
    {
        return _archived;
    }

    public void setArchived(boolean archived)
    {
        _archived = archived;
    }

    @Override
    public List<Plate> getPlates(User user)
    {
        ContainerFilter cf = PlateManager.get().getPlateContainerFilter(null, getContainer(), user);
        List<Plate> plates = new ArrayList<>();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("PlateSet"), _rowId);
        new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), Collections.singleton("RowId"), filter, null).forEach(Integer.class, plateId -> {
            plates.add(PlateCache.getPlate(cf, plateId));
        });

        return plates;
    }

    @JsonProperty("plateCount")
    public Integer getPlateCount()
    {
        if (_rowId == null)
            return 0;

        TableInfo table = AssayDbSchema.getInstance().getTableInfoPlate();
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ")
                .append(table, "P")
                .append(" WHERE PlateSet = ?")
                .add(_rowId);

        return new SqlSelector(table.getSchema(), sql).getObject(Integer.class);
    }
}
