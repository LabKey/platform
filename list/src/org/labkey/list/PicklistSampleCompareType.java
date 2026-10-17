/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.list;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PicklistSampleCompareType extends CompareType
{
    public PicklistSampleCompareType()
    {
        super("Samples for picklist", "picklistsamples", "PICKLIST_SAMPLES", true, " matches picklist ", null);
    }

    @Override
    public SimpleFilter.FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
    {
        final String listName = value.toString();
        final User user = (User) QueryService.get().getEnvironment(QueryService.Environment.USER);
        final Container container = (Container) QueryService.get().getEnvironment(QueryService.Environment.CONTAINER);
        if (user == null || container == null)
            return new SimpleFilter.FalseClause();

        ListDefinition listDef = ListService.get().getList(container, listName, true);
        if (listDef == null || !listDef.isPicklist())
            return new SimpleFilter.FalseClause();

        ContainerFilter cf = ListService.get().getPicklistContainerFilter(container, user, listDef);
        TableInfo listTable = listDef.getTable(user, container, cf);
        if (listTable == null)
            return new SimpleFilter.FalseClause();

        return new PicklistSamplesClause(fieldKey, listName, listTable);
    }

    /** Filters to the picklist's samples with a subquery, so a large picklist isn't loaded into an IN list. */
    private static class PicklistSamplesClause extends SimpleFilter.FilterClause
    {
        private final FieldKey _fieldKey;
        private final String _listName;
        private final TableInfo _listTable;
        private final ColumnInfo _sampleIdColumn;
        private final SQLFragment _picklistSql;

        PicklistSamplesClause(FieldKey fieldKey, String listName, TableInfo listTable)
        {
            _fieldKey = fieldKey;
            _listName = listName;
            _listTable = listTable;
            _sampleIdColumn = listTable.getColumn("SampleID");
            // The query table applies the picklist's container filter and permissions
            _picklistSql = QueryService.get().getSelectBuilder(listTable).columns(_sampleIdColumn).buildSqlFragment();
        }

        @Override
        public List<FieldKey> getFieldKeys()
        {
            return Collections.singletonList(_fieldKey);
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            ColumnInfo colInfo = null != columnMap ? columnMap.get(_fieldKey) : null;

            return new SQLFragment()
                    .appendIdentifier(SimpleFilter.getAliasForColumnFilter(_listTable.getSqlDialect(), colInfo, _fieldKey))
                    .append(" IN (SELECT ").appendIdentifier(_sampleIdColumn.getAlias())
                    .append(" FROM (").append(_picklistSql).append(") picklist)");
        }

        /** LabKey SQL can't embed the provisioned subquery, so fall back to the picklist's current sample ids. */
        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            Integer[] sampleIds = new TableSelector(_listTable, Collections.singleton("SampleID")).getArray(Integer.class);
            return new SimpleFilter.InClause(_fieldKey, Arrays.asList(sampleIds)).getLabKeySQLWhereClause(columnMap);
        }

        @Override
        protected int appendFilterValueText(StringBuilder sb, SimpleFilter.ColumnNameFormatter formatter)
        {
            int result = sb.length();
            sb.append(formatter.format(_fieldKey)).append(" matches picklist ").append(_listName);
            return result;
        }
    }
}
