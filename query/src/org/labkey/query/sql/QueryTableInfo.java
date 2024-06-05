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
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.HasResolvedTables;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
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

public class QueryTableInfo extends AbstractTableInfo implements ContainerFilterable, HasResolvedTables
{
    AbstractQueryRelation _relation;


    public QueryTableInfo(AbstractQueryRelation relation, String name)
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
        try (var recursion = Query.queryRecursionCheck("Too many tables used in this query.  Query may be recursive.", null))
        {
            SQLFragment sql = _relation.getSql();
            if (null == sql)
            {
                if (!_relation._query.getParseErrors().isEmpty())
                    throw _relation._query.getParseErrors().get(0);
                else
                    throw new QueryException("Error generating SQL");
            }
            SQLFragment f = new SQLFragment();
            f.append("(").append(sql).append(") ").append(alias);
            return f;
        }
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


    void afterInitializeColumns()
    {
        remapSelectFieldKeys(false);
    }


    @Override
    public void afterConstruct()
    {
        checkLocked();
        super.afterConstruct();
    }


    /* helper method to fixup fieldkeys for columns in QueryTableInfo  */
    public void remapSelectFieldKeys(boolean skipColumnLogging)
    {
        Query query = _relation._query;

        Map<String,FieldKey> outerMap = getUniqueKeyMap();
        CaseInsensitiveHashSet warnings = new CaseInsensitiveHashSet();

        for (var ci : getMutableColumns())
        {
            // Find if this column is associated with a ColumnResolvingRelation (e.g. QueryTable).
            // If it is, use that tables "remap" map to fix up column using ColumnInfo.remapFieldKeys().
            String uniqueName = ((AbstractQueryRelation.RelationColumnInfo) ci)._column.getUniqueName();
            AbstractQueryRelation.RelationColumn sourceColumn = query._mapUniqueNamesToRelationColumn.get(uniqueName);
            QueryRelation sourceRelation = null == sourceColumn ? null : sourceColumn.getTable();
            Map<FieldKey, FieldKey> remap = sourceRelation instanceof QueryRelation.ColumnResolvingRelation crr ? crr.getRemapMap(outerMap) : null;

            if (null != remap)
            {
                // QueryUnion handles ColumnLogging, so don't try to remap again
                ColumnLogging cl = ci.getColumnLogging();
                if (skipColumnLogging)
                    ci.setColumnLogging(null);
                ci.remapFieldKeys(null, remap, warnings, true);
                if (skipColumnLogging)
                    ci.setColumnLogging(cl);
                var queryWarnings = _relation._query.getParseWarnings();
                for (String w : warnings)
                    queryWarnings.add(new QueryParseWarning(w, null, 0, 0));
            }

            // handle QueryColumnLogging which need to be converted to a normal ColumnLogging
            if (ci.getColumnLogging() instanceof QueryColumnLogging qcl)
            {
                ci.setColumnLogging(qcl.remapQueryFieldKeys(this, ci.getFieldKey(), outerMap));
            }
            assert !(ci.getColumnLogging() instanceof QueryColumnLogging);
        }
    }


    private Map<String,FieldKey> getUniqueKeyMap()
    {
          // Create map of unique names to the SELECT output columns
        Map<String,FieldKey> mapQueryUniqueNamesToAlias = new HashMap<>();
        for (var e : _relation.getAllColumns().entrySet())
            mapQueryUniqueNamesToAlias.put(e.getValue().getUniqueName(), new FieldKey(null, e.getKey()));
        return mapQueryUniqueNamesToAlias;
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
