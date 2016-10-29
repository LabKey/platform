/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.MemTracker;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class QueryTableInfo extends AbstractTableInfo implements ContainerFilterable
{
    QueryRelation _relation;
    private ContainerFilter _containerFilter;

    public QueryTableInfo(QueryRelation relation, String name)
    {
        super(relation._query.getSchema().getDbSchema(), name);
        _relation = relation;

        assert MemTracker.getInstance().put(this);
    }


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

    public void setContainerFilter(@NotNull ContainerFilter containerFilter)
    {
        checkLocked();
        ContainerFilter.logSetContainerFilter(containerFilter, "Query", StringUtils.defaultString(_relation._query._name, "anonymous"));
        _containerFilter = containerFilter;
        _relation.setContainerFilter(containerFilter);
    }

    public boolean hasDefaultContainerFilter()
    {
        return _containerFilter == null;
    }

    @NotNull
    @Override
    public ContainerFilter getContainerFilter()
    {
        if (_containerFilter == null)
            return ContainerFilter.CURRENT;
        return _containerFilter;
    }

    public boolean hasSort()
    {
        return false;
    }

    @Override
    public void afterConstruct()
    {
        checkLocked();
        super.afterConstruct();
        remapFieldKeys();
    }

    public void remapFieldKeys()
    {
        initFieldKeyMap();
        for (ColumnInfo ci : getColumns())
        {
            Map<FieldKey, FieldKey> remap = mapFieldKeyToSiblings.get(ci.getFieldKey());
            if (null == remap || remap.isEmpty())
                continue;
            ci.remapFieldKeys(null, remap);
        }
    }


    // map output column to its related columns (grouped by source querytable)
    private Map<FieldKey, Map<FieldKey,FieldKey>> mapFieldKeyToSiblings = null;

    private void initFieldKeyMap()
    {
        if (mapFieldKeyToSiblings == null)
        {
            mapFieldKeyToSiblings = new TreeMap<>();
            Query query = _relation._query;
            for (Map.Entry<QueryTable,Map<FieldKey,QueryRelation.RelationColumn>> maps : query.qtableColumnMaps.entrySet())
            {
                Map<FieldKey,QueryRelation.RelationColumn> map = maps.getValue();
                Map<FieldKey,FieldKey> flippedMap = new TreeMap<>();
                for (Map.Entry<FieldKey,QueryRelation.RelationColumn> e : map.entrySet())
                {
                    flippedMap.put(e.getValue().getFieldKey(), e.getKey());
                    mapFieldKeyToSiblings.put(e.getKey(), flippedMap);
                }
            }
            mapFieldKeyToSiblings = Collections.unmodifiableMap(mapFieldKeyToSiblings);
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
}
