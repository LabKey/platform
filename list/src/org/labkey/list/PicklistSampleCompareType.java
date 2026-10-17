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
import org.labkey.api.data.ColumnRenderProperties;
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
import org.labkey.api.util.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

        return new PicklistSamplesClause(this, fieldKey, listName, container, listTable);
    }

    /** Filters to the picklist's samples with a subquery, so a large picklist isn't loaded into an IN list. */
    private static class PicklistSamplesClause extends SimpleFilter.FilterClause
    {
        private final CompareType _compareType;
        private final FieldKey _fieldKey;
        private final String _listName;
        private final Container _container;
        private final TableInfo _listTable;
        private final ColumnInfo _sampleIdColumn;
        private final SQLFragment _picklistSql;
        private Set<Long> _sampleIds;

        PicklistSamplesClause(CompareType compareType, FieldKey fieldKey, String listName, Container container, TableInfo listTable)
        {
            _compareType = compareType;
            _fieldKey = fieldKey;
            _listName = listName;
            _container = container;
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
                    .appendIdentifier(SimpleFilter.getAliasForColumnFilter(null != dialect ? dialect : _listTable.getSqlDialect(), colInfo, _fieldKey))
                    .append(" IN (SELECT ").appendIdentifier(_sampleIdColumn.getAlias())
                    .append(" FROM (").append(_picklistSql).append(") picklist)");
        }

        /** LabKey SQL can't embed the provisioned subquery, so fall back to the picklist's current sample ids. */
        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            return new SimpleFilter.InClause(_fieldKey, getSampleIds()).getLabKeySQLWhereClause(columnMap);
        }

        private Set<Long> getSampleIds()
        {
            if (null == _sampleIds)
                _sampleIds = new HashSet<>(Arrays.asList(new TableSelector(_listTable, Collections.singleton("SampleID")).getArray(Long.class)));
            return _sampleIds;
        }

        @Override
        protected boolean meetsCriteria(ColumnRenderProperties col, Object value)
        {
            return value instanceof Number n && getSampleIds().contains(n.longValue());
        }

        @Override
        public Map.Entry<String, String> toURLParam(String dataRegionPrefix)
        {
            return new Pair<>(dataRegionPrefix + _fieldKey + SimpleFilter.SEPARATOR_CHAR + _compareType.getPreferredUrlKey(), _listName);
        }

        @Override
        protected int appendFilterValueText(StringBuilder sb, SimpleFilter.ColumnNameFormatter formatter)
        {
            int result = sb.length();
            sb.append(formatter.format(_fieldKey)).append(" matches picklist ").append(_listName);
            return result;
        }

        @Override
        public boolean equals(Object o)
        {
            return o instanceof PicklistSamplesClause that && _fieldKey.equals(that._fieldKey) && _listName.equals(that._listName) && _container.equals(that._container);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(_fieldKey, _listName, _container);
        }
    }
}
