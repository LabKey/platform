package org.labkey.assay.plate;

import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.Entity;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlateSetImpl extends Entity implements PlateSet
{
    private int _rowId;
    private String _name;
    private boolean _archived;
    private Container _container;

    @Override
    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

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
}
