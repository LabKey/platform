package org.labkey.visualization.sql;

import org.labkey.api.util.Pair;

import java.util.*;

/**
 * Copyright (c) 2011 LabKey Corporation
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
 * <p/>
 * User: brittp
 * Date: Jun 7, 2011 2:25:40 PM
 */
public class OuterJoinSourceQuery implements IVisualizationSourceQuery
{
    private Collection<IVisualizationSourceQuery> _queries;

    public OuterJoinSourceQuery(Collection<IVisualizationSourceQuery> queries)
    {
        _queries = queries;
    }

    @Override
    public Set<VisualizationSourceColumn> getSelects(VisualizationSourceColumn.Factory factory, boolean includeRequiredExtraCols)
    {
        Set<VisualizationSourceColumn> selects = new LinkedHashSet<VisualizationSourceColumn>();
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
    public String getSQL(VisualizationSourceColumn.Factory factory) throws VisualizationSQLGenerator.GenerationException
    {
        return VisualizationSQLGenerator.getSQL(this, factory, _queries, Collections.<VisualizationIntervalColumn>emptyList(), "FULL OUTER JOIN", false);
    }

    @Override
    public VisualizationSourceQuery getJoinTarget()
    {
        return null;
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
        List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinConditions = new ArrayList<Pair<VisualizationSourceColumn, VisualizationSourceColumn>>();
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
        Set<VisualizationSourceColumn> sorts = new LinkedHashSet<VisualizationSourceColumn>();
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
    public String getSelectListName(Set<String> selectAliases)
    {
        String finalAlias = "\"" + selectAliases.iterator().next() + "\"";
        if (selectAliases.size() == 1)
            return finalAlias;
        // Rather than just choosing a single alias from one of our sub-queries, we need to coalesce the values
        // to make sure we have non-null results:
        StringBuilder coalesce = new StringBuilder();
        coalesce.append("COALESCE(");
        String sep = "";
        for (String alias : selectAliases)
        {
            coalesce.append(sep).append("\"").append(alias).append("\"");
            sep = ", ";
        }
        // Alias the coalesced value as the first alias:
        coalesce.append(") AS ").append(finalAlias);
        return coalesce.toString();
    }

    @Override
    public Map<String, Set<String>> getColumnNameToValueAliasMap(VisualizationSourceColumn.Factory factory)
    {
        Map<String, Set<String>> colMap = new LinkedHashMap<String, Set<String>>();
        for (IVisualizationSourceQuery query : _queries)
        {
            Map<String, Set<String>> queryColMap = query.getColumnNameToValueAliasMap(factory);
            for (Map.Entry<String, Set<String>> entry : queryColMap.entrySet())
            {
                Set<String> valueAliases = colMap.get(entry.getKey());
                if (valueAliases == null)
                {
                    valueAliases = new LinkedHashSet<String>();
                    colMap.put(entry.getKey(), valueAliases);
                }
                valueAliases.addAll(entry.getValue());
            }
        }
        return colMap;
    }
}
