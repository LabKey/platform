/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.HasResolvedTables;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseWarning;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.MemTracker;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class QueryTableInfo extends AbstractTableInfo implements ContainerFilterable, HasResolvedTables
{
    QueryRelation _relation;


    public QueryTableInfo(QueryRelation relation, String name)
    {
        super(relation._query.getSchema().getDbSchema(), name);
        _relation = relation;

        assert MemTracker.getInstance().put(this);
    }


    @Override
    @NotNull
    public SQLFragment getFromSQL()
    {
        throw new IllegalStateException();
    }


    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment f = new SQLFragment();
        SQLFragment sql = _relation.getSql();
        f.append("(").append(sql).append(") ").append(alias);
        return f;
    }


    @Override
    public @NotNull Collection<QueryService.ParameterDecl> getNamedParameters()
    {
        Query query = _relation._query;
        Collection<QueryService.ParameterDecl> ret = query.getParameters();
        if (null == ret)
            return Collections.emptyList();
        return ret;
    }

    @Override
    public boolean needsContainerClauseAdded()
    {
        // Let the underlying schemas do whatever filtering they need on the data, especially since
        // after columns are part of a query we lose track of what was on the base table and what's been joined in
        return false;
    }

    @Override
    public void setContainerFilter(@NotNull ContainerFilter containerFilter)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasDefaultContainerFilter()
    {
        return null!=getUserSchema() && getContainerFilter() == getUserSchema().getDefaultContainerFilter();
    }

    @NotNull
    @Override
    public ContainerFilter getContainerFilter()
    {
        return _relation._query.getContainerFilter();
    }


    @Override
    public final List<Sort.SortField> getSortFields()
    {
        return _relation.getSortFields();
    }


    protected void afterInitializeColumns()
    {
        remapFieldKeys();
    }


    @Override
    public void afterConstruct()
    {
        checkLocked();
        super.afterConstruct();
    }


    public void remapFieldKeys()
    {
        Query query = _relation._query;

        initFieldKeyMap();
        CaseInsensitiveHashSet warnings = new CaseInsensitiveHashSet();

        for (var ci : getMutableColumns())
        {
            // Find if this column is associated with a QueryTable.
            // If it is, use that tables "remap" map to fix up column using ColumnInfo.remapFieldKeys().
            String uniqueName = ((QueryRelation.RelationColumnInfo) ci)._column.getUniqueName();
            QueryRelation sourceRelation = query._mapUniqueNamesToQueryRelation.get(uniqueName);
            Map<FieldKey, FieldKey> remap = sourceRelation instanceof QueryTable qt ? mapFieldKeyToSiblings.get(qt) : null;

            if (null != remap)
            {
//                try
                {
                    ci.remapFieldKeys(null, remap, warnings, true);
                    var queryWarnings = _relation._query.getParseWarnings();
                    for (String w : warnings)
                        queryWarnings.add(new QueryParseWarning(w, null, 0, 0));
                }
//                catch (MutableColumnInfo.RemapFieldKeysException fke)
//                {
//                    throw new QueryException(fke.getMessage(), fke);
//                }
            }

            // handle QueryColumnLogging which need to be converted to a normal ColumnLogging
            if (ci.getColumnLogging() instanceof QueryColumnLogging qcl)
            {
//                try
                {
                    ci.setColumnLogging(qcl.remapQueryFieldKeys(this, ci));
                }
//                catch (MutableColumnInfo.RemapFieldKeysException rfke)
//                {
//                    QueryParseWarning w = new QueryParseWarning(rfke.getMessage(), null, 0, 0);
//                    _relation._query.getParseWarnings().add(w);
//                    ci.setColumnLogging(ColumnLogging.error(w.getM));
//                }
            }
            assert !(ci.getColumnLogging() instanceof QueryColumnLogging);
        }
    }


    // map output column to its related columns (grouped by source querytable)
    Map<QueryTable, Map<FieldKey,FieldKey>> mapFieldKeyToSiblings = null;

    private void initFieldKeyMap()
    {
        Query query = _relation._query;

        assert null == query._mapQueryUniqueNamesToSelectAlias;
        assert query._mapUniqueNamesToQueryRelation.isEmpty();
        assert null == mapFieldKeyToSiblings;

        // Create map of unique names to the SELECT output columns
        Map<String,FieldKey> mapQueryUniqueNamesToAlias = new HashMap<>();
        for (var e : _relation.getAllColumns().entrySet())
            mapQueryUniqueNamesToAlias.put(e.getValue().getUniqueName(), new FieldKey(null, e.getKey()));
        query._mapQueryUniqueNamesToSelectAlias = Collections.unmodifiableMap(mapQueryUniqueNamesToAlias);


        // For each QueryTable, create a map from table field keys to selected column field keys
        mapFieldKeyToSiblings = new HashMap<>();
        for (QueryTable queryTable : query._qtables.keySet())
        {
            Map<FieldKey, FieldKey> remap = new TreeMap<>();
            for (var e : queryTable.getSelectedColumns().entrySet())
            {
                var tc = e.getValue();
                FieldKey tableFieldKey = tc.getFieldKey();
                String uniqueNane = tc.getUniqueName();
                query._mapUniqueNamesToQueryRelation.put(tc.getUniqueName(), queryTable);
                FieldKey targetFk = query._mapQueryUniqueNamesToSelectAlias.get(uniqueNane);
                if (null != targetFk)
                    remap.put(tableFieldKey, targetFk);
            }
            mapFieldKeyToSiblings.put(queryTable, remap);
        }
    }


    @Override
    public UserSchema getUserSchema()
    {
        return _relation.getSchema() instanceof UserSchema ? (UserSchema)_relation.getSchema() : null;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        UserSchema schema = getUserSchema();
        return schema != null && perm.equals(ReadPermission.class) && schema.getContainer().hasPermission(user, perm);
    }

    @Override
    public Set<ColumnInfo> getAllInvolvedColumns(Collection<ColumnInfo> selectColumns)
    {
        Set<ColumnInfo> allInvolvedColumns = super.getAllInvolvedColumns(selectColumns);
        for (QueryTable.TableColumn tableColumn : _relation._query.getInvolvedTableColumns())
            allInvolvedColumns.add(tableColumn._col);
        return allInvolvedColumns;
    }

    @Override
    public Set<SchemaKey> getResolvedTables()
    {
        return _relation.getResolvedTables();
    }

    @Override
    public boolean allowQueryTableURLOverrides()
    {
        return true;
    }
}
