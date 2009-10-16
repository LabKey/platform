package org.labkey.study.query;

import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.FieldKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.jetbrains.annotations.NotNull;

import java.sql.Types;
import java.util.List;
import java.util.ArrayList;

/**
 * Copyright (c) 2008-2009 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: Apr 30, 2008 11:13:56 AM
 */
public class DataSetColumnsTable extends FilteredTable
{
    public DataSetColumnsTable(final StudyQuerySchema schema)
    {
        super(ExperimentService.get().getTinfoPropertyDescriptor(), schema.getContainer());
        setDescription("Metadata table containing one row of metadata for each column in all study datasets.");
        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        SQLFragment sql = new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".DataSetId");
        ColumnInfo datasetLookupCol = new ExprColumn(this, "DataSet", sql, Types.INTEGER);
        datasetLookupCol.setFk(new LookupForeignKey("DataSetId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new DataSetsTable(schema);
            }
        });
        addColumn(datasetLookupCol);
        defaultCols.add(FieldKey.fromParts("DataSet", "DataSetId"));
        defaultCols.add(FieldKey.fromParts("DataSet", "Name"));
        defaultCols.add(FieldKey.fromParts("DataSet", "Label"));

        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("Container".equalsIgnoreCase(name) || "EntityId".equalsIgnoreCase(name))
                continue;
            addWrapColumn(baseColumn);
            defaultCols.add(FieldKey.fromParts(name));
        }
        setDefaultVisibleColumns(defaultCols);
    }

    @Override @NotNull
    public SQLFragment getFromSQL()
    {
        SQLFragment sql = super.getFromSQL();

        SQLFragment result = new SQLFragment("(SELECT DataSet.DataSetId, PropertyDescriptor.* FROM ");
        result.append("(").append(sql).append(") AS PropertyDescriptor");
        result.append("\n" +
                "JOIN exp.PropertyDomain AS PropertyDomain ON PropertyDomain.PropertyId = PropertyDescriptor.PropertyId\n" +
                "JOIN exp.DomainDescriptor AS DomainDescriptor ON DomainDescriptor.DomainId = PropertyDomain.DomainId\n" +
                "JOIN study.DataSet AS DataSet ON DataSet.TypeURI = DomainDescriptor.DomainURI)\n");
        return result;
    }
}
