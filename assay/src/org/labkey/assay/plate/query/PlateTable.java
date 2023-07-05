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

package org.labkey.assay.plate.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: brittp
 * Date: Nov 1, 2006
 * Time: 4:37:02 PM
 */
public class PlateTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public static final String NAME = "Plate";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts("Name"));
        defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("Modified"));
        defaultVisibleColumns.add(FieldKey.fromParts("ModifiedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("Template"));
        defaultVisibleColumns.add(FieldKey.fromParts("Rows"));
        defaultVisibleColumns.add(FieldKey.fromParts("Columns"));
        defaultVisibleColumns.add(FieldKey.fromParts("Type"));
    }

    public PlateTable(PlateSchema schema, @Nullable ContainerFilter cf)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoPlate(), cf);
        setTitleColumn("Name");
    }

    @Override
    public void addColumns()
    {
        super.addColumns();
        addColumn(createPropertiesColumn());
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    private MutableColumnInfo createPropertiesColumn()
    {
        ColumnInfo lsidCol = getColumn("LSID", false);
        var col = new AliasedColumn(this, "Properties", lsidCol);
        col.setDescription("Properties associated with this Plate");
        col.setHidden(true);
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setCalculated(true);
        col.setIsUnselectable(true);

        String propPrefix = new Lsid("PlateTemplate", "Folder-" + getContainer().getRowId(), "objectType#").toString();
        SimpleFilter filter = SimpleFilter.createContainerFilter(getContainer());
        filter.addCondition(FieldKey.fromParts("PropertyURI"), propPrefix, CompareType.STARTS_WITH);
        final Map<String, PropertyDescriptor> map = new TreeMap<>();

        new TableSelector(OntologyManager.getTinfoPropertyDescriptor(), filter, null).forEach(PropertyDescriptor.class, pd -> {
            if (pd.getPropertyType() == PropertyType.DOUBLE)
                pd.setFormat("0.##");
            map.put(pd.getName(), pd);
        });
        col.setFk(new PropertyForeignKey(getUserSchema(), getContainerFilter(), map));

        return col;
    }
}
