/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.util.List;
import java.util.stream.Collectors;

import static org.labkey.api.util.PageFlowUtil.encodeURIComponent;

/**
 * Reference a single row within a table by its query coordinates: container, schemaName, queryName, and a set of pk filters.
 */
public class QueryRowReference
{
    final @NotNull Container _container;
    final @NotNull SchemaKey _schemaKey;
    final @NotNull String _queryName;
    final @NotNull List<Pair<FieldKey, Object>> _pkFilters;

    public QueryRowReference(@NotNull Container c, @NotNull SchemaKey schemaKey, @NotNull String queryName, @NotNull Enum pkCol, long pkValue)
    {
        this(c, schemaKey, queryName, List.of(Pair.of(FieldKey.fromParts(pkCol), pkValue)));
    }

    public QueryRowReference(@NotNull Container c, @NotNull SchemaKey schemaKey, @NotNull String queryName, @NotNull FieldKey pkCol, long pkValue)
    {
        this(c, schemaKey, queryName, List.of(Pair.of(pkCol, pkValue)));
    }

    public QueryRowReference(@NotNull Container c, @NotNull SchemaKey schemaKey, @NotNull String queryName, @NotNull FieldKey pkCol, @NotNull String pkValue)
    {
        this(c, schemaKey, queryName, List.of(Pair.of(pkCol, pkValue)));
    }

    public QueryRowReference(@NotNull Container c, @NotNull SchemaKey schemaKey, @NotNull String queryName, @NotNull Pair<FieldKey, Object> pkFilter)
    {
        this(c, schemaKey, queryName, List.of(pkFilter));
    }

    public QueryRowReference(@NotNull Container c, @NotNull SchemaKey schemaKey, @NotNull String queryName, @NotNull List<Pair<FieldKey, Object>> pkFilters)
    {
        _container = c;
        _schemaKey = schemaKey;
        _queryName = queryName;
        _pkFilters = pkFilters;
        if (pkFilters.isEmpty())
            throw new IllegalArgumentException();
    }

    public @NotNull Container getContainer()
    {
        return _container;
    }

    public @NotNull SchemaKey getSchemaKey()
    {
        return _schemaKey;
    }

    public @NotNull String getQueryName()
    {
        return _queryName;
    }

    public @NotNull List<Pair<FieldKey, Object>> getPkFilters()
    {
        return _pkFilters;
    }

    public ActionURL toExecuteQueryURL()
    {
        ActionURL url = QueryService.get().urlDefault(_container, QueryAction.executeQuery, _schemaKey.toString(), _queryName);
        _pkFilters.forEach(f -> {
            url.addFilter(QueryView.DATAREGIONNAME_DEFAULT, f.first, CompareType.EQUAL, String.valueOf(f.second));
        });
        return url;
    }

    /**
     * Create URL query params representing the schemaName, queryName, and pkFilters
     * similar to {@link CustomViewXmlReader.getFilterAndSortString}
     *
     * e.g, <code>schemaName=exp&amp;queryName=Data&amp;query.rowId~eq=1234</code>
     */
    public String toFilterAndSortString()
    {
        StringBuilder ret = new StringBuilder();

        ret.append(QueryParam.schemaName).append("=").append(encodeURIComponent(_schemaKey.toString()));
        ret.append("&");
        ret.append(QueryParam.queryName).append("=").append(encodeURIComponent(_queryName));
        for (var f : _pkFilters)
        {
            ret.append("&");
            ret.append(QueryView.DATAREGIONNAME_DEFAULT).append(".").append(encodeURIComponent(f.first.toString()));
            ret.append("~");
            ret.append(CompareType.EQUAL.getPreferredUrlKey());
            ret.append("=");
            ret.append(encodeURIComponent(String.valueOf(f.second)));
        }

        return ret.toString();
    }

    /**
     * Compact form of the query coordinates for debugging.
     */
    @Override
    public String toString()
    {
        return _schemaKey + "." + _queryName + "&" + _pkFilters.stream().map(f -> encodeURIComponent(f.first.toString()) + "=" + encodeURIComponent(String.valueOf(f.second))).collect(Collectors.joining("&"));
    }
}
