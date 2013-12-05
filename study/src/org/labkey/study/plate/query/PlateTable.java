/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.study.plate.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.study.StudySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: brittp
 * Date: Nov 1, 2006
 * Time: 4:37:02 PM
 */
public class PlateTable extends BasePlateTable
{
    public PlateTable(PlateSchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoPlate());
        setPublicSchemaName(PlateSchema.SCHEMA_NAME);
        final FieldKey keyProp = new FieldKey(null, "Property");
        final List<FieldKey> visibleColumns = new ArrayList<>();
        addWrapColumn(_rootTable.getColumn("RowId"));
        addWrapColumn(_rootTable.getColumn("Name"));
        setTitleColumn("Name");
        ColumnInfo templateCol =_rootTable.getColumn("Template");
        addWrapColumn(templateCol);
        addCondition(templateCol, "0");
        addWrapColumn(_rootTable.getColumn("Created"));
        visibleColumns.add(FieldKey.fromParts("Created"));
        addWrapColumn(_rootTable.getColumn("CreatedBy"));
        addWrapColumn(_rootTable.getColumn("Type"));
        visibleColumns.add(FieldKey.fromParts("CreatedBy"));

        //String sqlObjectId = "( SELECT objectid FROM exp.object WHERE exp.object.objecturi = " + ExprColumn.STR_TABLE_ALIAS + ".lsid)";

        //ColumnInfo colProperty = new ExprColumn(this, "property", new SQLFragment(sqlObjectId), Types.INTEGER);
        ColumnInfo colProperty = wrapColumn("property", _rootTable.getColumn("lsid"));
        String propPrefix = new Lsid("PlateInstance", "Folder-" + schema.getContainer().getRowId(), "").toString();
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("PropertyURI"), propPrefix, CompareType.STARTS_WITH);
        final Map<String, PropertyDescriptor> map = new TreeMap<>();

        new TableSelector(OntologyManager.getTinfoPropertyDescriptor(), filter, null).forEach(new Selector.ForEachBlock<PropertyDescriptor>()
        {
            @Override
            public void exec(PropertyDescriptor pd) throws SQLException
            {
                if (pd.getPropertyType() == PropertyType.DOUBLE)
                    pd.setFormat("0.##");
                map.put(pd.getName(), pd);
                visibleColumns.add(new FieldKey(keyProp, pd.getName()));

            }
        }, PropertyDescriptor.class);

        colProperty.setFk(new PropertyForeignKey(map, schema));
        colProperty.setIsUnselectable(true);
        addColumn(colProperty);
        setDefaultVisibleColumns(visibleColumns);
    }

    protected String getPlateIdColumnName()
    {
        return "RowId";
    }
}
