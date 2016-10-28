/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.visualization.sql;

import org.labkey.api.data.Container;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Pair;
import org.labkey.api.visualization.IVisualizationSourceQuery;
import org.labkey.api.visualization.SQLGenerationException;
import org.labkey.api.visualization.VisualizationAggregateColumn;
import org.labkey.api.visualization.VisualizationIntervalColumn;
import org.labkey.api.visualization.VisualizationSourceColumn;

import java.util.*;

/**
 * User: brittp
 * Date: Jun 7, 2011 2:25:40 PM
 */
public class OuterJoinSourceQuery implements IVisualizationSourceQuery
{
    private Collection<IVisualizationSourceQuery> _queries;
    private boolean _hasRowLimit;
    private VisualizationSQLGenerator _generator;

    public OuterJoinSourceQuery(VisualizationSQLGenerator generator, Collection<IVisualizationSourceQuery> queries, boolean hasRowLimit)
    {
        _generator = generator;
        _queries = queries;
        _hasRowLimit = hasRowLimit;
    }

    @Override
    public Set<VisualizationSourceColumn> getSelects(VisualizationSourceColumn.Factory factory, boolean includeRequiredExtraCols)
    {
        Set<VisualizationSourceColumn> selects = new LinkedHashSet<>();
        for (IVisualizationSourceQuery query : _queries)
            selects.addAll(query.getSelects(factory, includeRequiredExtraCols));
        return selects;
    }

    @Override
    public VisualizationSourceColumn getPivot()
    {
        return null;
    }

    @Override
    public String getSQL(VisualizationSourceColumn.Factory factory) throws SQLGenerationException
    {
        return _generator.getSubselectSQL(this, factory, _queries, Collections.emptyList(), "FULL OUTER JOIN", false, _hasRowLimit);
    }

    @Override
    public VisualizationSourceQuery getJoinTarget()
    {
        return null;
    }

    @Override
    public void addSelect(VisualizationSourceColumn select, boolean measure)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSQLAlias()
    {
        return "\"" + getAlias() + "\"";
    }

    @Override
    public String getAlias()
    {
        StringBuilder alias = new StringBuilder();
        String sep = "";
        for (IVisualizationSourceQuery query : _queries)
        {
            alias.append(sep).append(query.getAlias());
            sep = "_";
        }
        return alias.toString();
    }

    @Override
    public List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> getJoinConditions()
    {
        List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinConditions = new ArrayList<>();
        for (IVisualizationSourceQuery query : _queries)
        {
            if (query.getJoinConditions() != null)
                joinConditions.addAll(query.getJoinConditions());
        }
        return joinConditions;
    }

    @Override
    public Set<VisualizationSourceColumn> getSorts()
    {
        Set<VisualizationSourceColumn> sorts = new LinkedHashSet<>();
        for (IVisualizationSourceQuery query : _queries)
            sorts.addAll(query.getSorts());
        return sorts;
    }

    @Override
    public Set<VisualizationAggregateColumn> getAggregates()
    {
        // Queries within this join query may have aggregates, but the high-level join does not:
        return Collections.emptySet();
    }

    @Override
    public boolean contains(VisualizationSourceColumn column)
    {
        for (IVisualizationSourceQuery query : _queries)
        {
            if (query.contains(column))
                return true;
        }
        return false;
    }

    @Override
    public String getSelectListName(Set<VisualizationSourceColumn> selectAliases)
    {
        VisualizationSourceColumn finalAlias = selectAliases.iterator().next();
        if (selectAliases.size() == 1)
            return finalAlias.getSQLAlias();
        // Rather than just choosing a single alias from one of our sub-queries, we need to coalesce the values
        // to make sure we have non-null results:
        StringBuilder coalesce = new StringBuilder();
        coalesce.append("COALESCE(");
        String sep = "";
        for (VisualizationSourceColumn alias : selectAliases)
        {
            coalesce.append(sep).append(alias.getSQLAlias());
            sep = ", ";

            // issue 20526: inner join gets confused about which alias to use for the join condition after coalesce
            alias.setOtherAlias(finalAlias.getAlias());
        }
        // Alias the coalesced value as the first alias:
        coalesce.append(") AS ").append(finalAlias.getSQLAlias());
        return coalesce.toString();
    }

    @Override
    public Map<String, Set<VisualizationSourceColumn>> getColumnNameToValueAliasMap(VisualizationSourceColumn.Factory factory, boolean measuresOnly)
    {
        Map<String, Set<VisualizationSourceColumn>> colMap = new LinkedHashMap<String, Set<VisualizationSourceColumn>>()
        {
            @Override
            public Set<VisualizationSourceColumn> get(Object o)
            {
                Set<VisualizationSourceColumn> set = super.get(o);
                if (null == set)
                    put((String)o, set = new LinkedHashSet<>());
                return set;
            }
        };

        for (IVisualizationSourceQuery query : _queries)
        {
            Map<String, Set<VisualizationSourceColumn>> queryColMap = query.getColumnNameToValueAliasMap(factory, measuresOnly);
            for (Map.Entry<String, Set<VisualizationSourceColumn>> entry : queryColMap.entrySet())
            {
                Set<VisualizationSourceColumn> valueAliases = colMap.get(entry.getKey());
                valueAliases.addAll(entry.getValue());
            }
        }
        return colMap;
    }

    @Override
    public UserSchema getSchema()
    {
        Iterator<IVisualizationSourceQuery> i = _queries.iterator();
        UserSchema result = i.next().getSchema();
        while (i.hasNext())
        {
            UserSchema schema = i.next().getSchema();
            if (!schema.getContainer().equals(result.getContainer()) || !schema.getName().equals(result.getName()) || !schema.getUser().equals(result.getUser()))
            {
                throw new IllegalStateException("All source schemas must be the same");
            }
        }
        return result;
    }

    @Override
    public Container getContainer()
    {
        Iterator<IVisualizationSourceQuery> i = _queries.iterator();
        Container result = i.next().getContainer();
        while (i.hasNext())
        {
            if (!result.equals(i.next().getContainer()))
            {
                throw new IllegalStateException("All source queries must be from the same container");
            }
        }
        return result;
    }

    @Override
    public String getQueryName()
    {
        return _queries.iterator().next().getQueryName();
    }

    @Override
    public boolean isSkipVisitJoin()
    {
        return false;
    }

    public boolean isVisitTagQuery()
    {
        return false;
    }

    public boolean isRequireLeftJoin()
    {
        return false;
    }
}
