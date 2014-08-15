/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
 * Created: Apr 30, 2008 11:13:43 AM
 */
public class DataSetsTable extends FilteredTable<StudyQuerySchema>
{
    public DataSetsTable(StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoDataSet(), schema);
        setName("Datasets");
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();

            ColumnInfo colInfo = addWrapColumn(baseColumn);
            if ("Container".equalsIgnoreCase(name) || "EntityId".equalsIgnoreCase(name))
                colInfo.setHidden(true);
        }

        // add a column to indicate whether the dataset is sourced from a query snapshot
        ExprColumn result = new ExprColumn(this, "QuerySnapshot", new SQLFragment("~~PLACEHOLDER~~"), JdbcType.BOOLEAN)
        {
            @Override
            public SQLFragment getValueSql(String tableAlias)
            {
                TableInfo tinfo = QuerySnapshotService.get(StudySchema.getInstance().getSchemaName()).getTableInfoQuerySnapshotDef();
                SqlDialect d = getSqlDialect();

                SQLFragment sql = new SQLFragment("(CASE WHEN EXISTS (SELECT RowId FROM ");
                sql.append(tinfo, "qs");
                sql.append(" WHERE 'qs.schema' = '").append(StudySchema.getInstance().getSchemaName()).append("' AND ").append(tableAlias).append(".Name = qs.Name AND ");
                sql.append(tableAlias).append(".Container = qs.Container) THEN " + d.getBooleanTRUE() + " ELSE " + d.getBooleanFALSE() + " END)");
                
                return sql;
            }
        };
        result.setDescription("Whether the source is from a Query Snapshot");
        addColumn(result);

        setTitleColumn("Label");

        getColumn("Container").setFk(new ContainerForeignKey(schema));
    }


    @Override
    protected SimpleFilter getFilter()
    {
        return getDatasetFilter(getContainer());
    }


    static SimpleFilter getDatasetFilter(Container c)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        if (null == study)
            return new SimpleFilter().addClause(new SimpleFilter.FalseClause());
        List<String> entityIds = new ArrayList<>();
        Set<String> containerIds = new HashSet<>();
        for (DataSet ds : study.getDatasets())
        {
            entityIds.add(ds.getEntityId());
            containerIds.add(((DataSetDefinition)ds).getDefinitionContainer().getId());
        }
        if (entityIds.isEmpty() || containerIds.isEmpty())
            return new SimpleFilter().addClause(new SimpleFilter.FalseClause());
        SimpleFilter f = new SimpleFilter();
        f.addInClause(new FieldKey(null,"container"),containerIds);
        f.addInClause(new FieldKey(null,"entityid"),entityIds);
        return f;
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
