/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;

import java.util.ArrayList;
import java.util.List;

/**
 * User: brittp
 * Created: Apr 30, 2008 11:13:56 AM
 */
public class DatasetColumnsTable extends FilteredTable<StudyQuerySchema>
{
    public static final String NAME = "DataSetColumns";

    public DatasetColumnsTable(final StudyQuerySchema schema)
    {
        super(ExperimentService.get().getTinfoPropertyDescriptor(), schema);
        setName(NAME);
        setDescription("Metadata table containing one row of metadata for each column in all study datasets.");
        List<FieldKey> defaultCols = new ArrayList<>();
        SQLFragment sql = new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".DataSetId");
        ColumnInfo datasetLookupCol = new ExprColumn(this, "DataSet", sql, JdbcType.INTEGER);
        datasetLookupCol.setFk(new LookupForeignKey("DataSetId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new DatasetsTable(schema);
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
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment result = new SQLFragment();
        result.appendComment("<DataSetColumnsTable>", getSqlDialect());
        result.append("(SELECT DataSet.DataSetId, PropertyDescriptor.* FROM ");
        result.append(super.getFromSQL("PropertyDescriptor"));
        result.append("\n" +
                "JOIN exp.PropertyDomain AS PropertyDomain ON PropertyDomain.PropertyId = PropertyDescriptor.PropertyId\n" +
                "JOIN exp.DomainDescriptor AS DomainDescriptor ON DomainDescriptor.DomainId = PropertyDomain.DomainId\n" +
                "JOIN (SELECT * FROM study.DataSet ");
        SQLFragment datasetFilter = DatasetsTable.getDatasetFilter(getContainer()).getSQLFragment(getSqlDialect());
        result.append(datasetFilter);
        result.append(") AS DataSet ON DataSet.TypeURI = DomainDescriptor.DomainURI) ");
        result.append(alias);
        result.appendComment("</DataSetColumnsTable>", getSqlDialect());
        return result;
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        assert null == filter || ContainerFilter.CURRENT == filter;
    }
}
