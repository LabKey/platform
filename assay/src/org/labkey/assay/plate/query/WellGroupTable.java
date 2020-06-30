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

import org.labkey.assay.query.AssayDbSchema;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.assay.plate.WellGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: brittp
 * Date: Nov 1, 2006
 * Time: 4:37:02 PM
 */
public class WellGroupTable extends BasePlateTable
{
    public WellGroupTable(PlateSchema schema, ContainerFilter cf, WellGroup.Type groupType)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoWellGroup(), cf);
        final FieldKey keyProp = new FieldKey(null, "Property");
        final List<FieldKey> visibleColumns = new ArrayList<>();
        addWrapColumn(_rootTable.getColumn("RowId"));
        addWrapColumn(_rootTable.getColumn("Name"));
        visibleColumns.add(FieldKey.fromParts("Name"));
        setTitleColumn("Name");
        var typeCol = _rootTable.getColumn("TypeName");
        addWrapColumn(typeCol);
        if (groupType != null)
            addCondition(typeCol, groupType.name());
        var templateCol = _rootTable.getColumn("Template");
        addWrapColumn(templateCol);
        visibleColumns.add(FieldKey.fromParts(templateCol.getName()));
        addCondition(templateCol, "0");
        var plateIdColumn = new AliasedColumn(this, "Plate", _rootTable.getColumn("PlateId"));
        plateIdColumn.setFk(new LookupForeignKey(null, (String) null, "RowId", null)
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return new PlateTable(_userSchema, cf);
            }
        });
        addColumn(plateIdColumn);
        visibleColumns.add(FieldKey.fromParts(plateIdColumn.getName()));

        //String sqlObjectId = "( SELECT objectid FROM exp.object WHERE exp.object.objecturi = " + ExprColumn.STR_TABLE_ALIAS + ".lsid)";

        //var colProperty = new ExprColumn(this, "property", new SQLFragment(sqlObjectId), Types.INTEGER);
        var colProperty = wrapColumn("property", _rootTable.getColumn("lsid"));
        String propPrefix = new Lsid("WellGroupInstance", "Folder-" + schema.getContainer().getRowId(), "").toString();
        SimpleFilter filter = SimpleFilter.createContainerFilter(schema.getContainer());
        filter.addCondition(FieldKey.fromParts("PropertyURI"), propPrefix, CompareType.STARTS_WITH);
        final Map<String, PropertyDescriptor> map = new TreeMap<>();

        new TableSelector(OntologyManager.getTinfoPropertyDescriptor(), filter, null).forEach(pd -> {
            if (pd.getPropertyType() == PropertyType.DOUBLE)
                pd.setFormat("0.##");
            map.put(pd.getName(), pd);
            visibleColumns.add(new FieldKey(keyProp, pd.getName()));

        }, PropertyDescriptor.class);

        colProperty.setFk(new PropertyForeignKey(schema, null, map));
        colProperty.setIsUnselectable(true);
        addColumn(colProperty);
        setDefaultVisibleColumns(visibleColumns);
    }

    @Override
    protected String getPlateIdColumnName()
    {
        return "Plate";
    }
}
