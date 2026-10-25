/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.query.suggestions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.PHI;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.suggestions.FilterSuggestion;
import org.labkey.api.query.suggestions.FilterSuggestionContext;
import org.labkey.api.query.suggestions.FilterSuggestionProvider;
import org.labkey.api.query.suggestions.SuggestionColumn;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.TracedOperation;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Backs query-getFilterSuggestions.api: resolves the table, describes its candidate columns, and ranks suggestions. */
public class FilterSuggestionService
{
    private static final Logger LOG = LogHelper.getLogger(FilterSuggestionService.class, "Grid search filter suggestions");
    private static final int MAX_SUGGESTIONS_LIMIT = 100;
    private static final String FILTER_REGION = "query";
    private static final String ALL_COLUMNS = "*";

    /**
     * @param columns    the grid's display column fieldKeys
     * @param parameters values for a parameterized query, applied to every read of the table's data
     * @param filters    the grid's base filters, which bound the rows facts are read from
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FilterSuggestionsRequest(
        String schemaName,
        String queryName,
        @Nullable String viewName,
        @Nullable String containerFilter,
        @Nullable Map<String, Object> parameters,
        @Nullable List<RequestFilter> filters,
        @Nullable List<String> columns,
        @Nullable String term,
        @Nullable Integer maxSuggestions
    ) {}

    /** A filter in its URL form, e.g. BoxId~eq=1 is {fieldKey: "BoxId", op: "eq", value: "1"} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequestFilter(String fieldKey, String op, @Nullable String value) {}

    /** @param complete false when at least one source of column facts failed or ran out of time */
    public record FilterSuggestionsResponse(@NotNull List<FilterSuggestion> suggestions, boolean complete) {}

    private record Context(TableInfo table, Container container, User user) implements FilterSuggestionContext
    {
        @Override
        public @NotNull TableInfo getTable()
        {
            return table;
        }

        @Override
        public @NotNull ContainerFilter getContainerFilter()
        {
            return Objects.requireNonNullElseGet(table.getContainerFilter(), () -> ContainerFilter.current(container, user));
        }

        @Override
        public @NotNull Container getContainer()
        {
            return container;
        }

        @Override
        public @NotNull User getUser()
        {
            return user;
        }
    }

    public static @NotNull FilterSuggestionsResponse getSuggestions(@NotNull User user, @NotNull Container container, @NotNull FilterSuggestionsRequest request)
    {
        SuggestionTerm term = SuggestionTerm.parse(request.term());
        if (term.isBlank())
            return new FilterSuggestionsResponse(List.of(), true);

        // Same checks and messages as QueryForm, so this fails like query-getQuery.api
        UserSchema schema = StringUtils.isBlank(request.schemaName()) ? null : QueryService.get().getUserSchema(user, container, request.schemaName());
        QueryForm.ensureSchemaNotNull(schema);
        if (StringUtils.isBlank(request.queryName()))
            throw new NotFoundException("Query not specified");

        TableInfo table = schema.getTable(request.queryName(), getContainerFilter(request, container, user));
        if (table == null)
            throw new NotFoundException("The specified query '" + request.queryName() + "' does not exist in schema '" + request.schemaName() + "'");
        if (!table.hasPermission(user, ReadPermission.class))
            throw new UnauthorizedException();
        SimpleFilter filter = getFilter(request, container, user);
        // A filter on a column whose data may not be read would reveal it through the rows the filter selects
        boolean readsData = filter == null || isReadable(table, filter);

        // No cached value or sample name is longer, so only the search can match
        if (term.getRaw().length() > ColumnFactsLoader.MAX_VALUE_LENGTH)
            return new FilterSuggestionsResponse(List.of(FilterSuggestion.search(term.getRaw())), true);

        FilterSuggestionColumns.Described described = FilterSuggestionColumns.describe(table, getView(schema, request, user), request.columns());
        List<SuggestionColumn> columns = described.columns();

        boolean complete = true;
        FilterSuggestionContext context = new Context(table, container, user);
        for (FilterSuggestionProvider provider : QueryService.get().getFilterSuggestionProviders())
        {
            try
            {
                if (provider.handles(table))
                    provider.describe(context, columns);
            }
            catch (RuntimeException e)
            {
                LOG.warn("Filter suggestion provider {} failed for {}.{}", provider.getClass().getName(), request.schemaName(), request.queryName(), e);
                complete = false;
            }
        }

        Function<TableInfo, String> tokens = FilterSuggestionService::getChangeToken;
        if (!allowsSharedFacts(context))
            tokens = _ -> null;

        try
        {
            if (readsData)
                complete &= ColumnFactsCache.get().fill(context, request.parameters(), filter, described, tokens, ColumnFactsCache.DEFAULT_BUDGET_MS);
        }
        catch (RuntimeException e)
        {
            LOG.warn("Unable to read column facts for {}.{}", request.schemaName(), request.queryName(), e);
            complete = false;
        }

        List<FilterSuggestion> suggestions = FilterSuggestionRanker.rank(term, columns, getMaxSuggestions(request));
        TracedOperation.tagActiveSpan("labkey.query", table.getPublicSchemaName() + "." + table.getPublicName());
        TracedOperation.tagActiveSpan("labkey.suggestion.count", suggestions.size());
        TracedOperation.tagActiveSpan("labkey.suggestion.complete", complete);
        return new FilterSuggestionsResponse(suggestions, complete);
    }

    /** The first non-null token from a provider that handles the table; null keeps its facts per user. */
    private static @Nullable String getChangeToken(TableInfo table)
    {
        for (FilterSuggestionProvider provider : QueryService.get().getFilterSuggestionProviders())
        {
            try
            {
                if (provider.handles(table))
                {
                    String token = provider.getChangeToken(table);
                    if (token != null)
                        return token;
                }
            }
            catch (RuntimeException e)
            {
                LOG.warn("Filter suggestion provider {} failed to supply a change token for {}", provider.getClass().getName(), table.getName(), e);
            }
        }
        return null;
    }

    /** False when any provider that handles the table, or fails to answer, rules out sharing its facts across users. */
    private static boolean allowsSharedFacts(FilterSuggestionContext context)
    {
        TableInfo table = context.getTable();
        for (FilterSuggestionProvider provider : QueryService.get().getFilterSuggestionProviders())
        {
            try
            {
                if (provider.handles(table) && !provider.allowsSharedFacts(context))
                    return false;
            }
            catch (RuntimeException e)
            {
                LOG.warn("Filter suggestion provider {} failed to say whether facts for {} can be shared", provider.getClass().getName(), table.getName(), e);
                return false;
            }
        }
        return true;
    }

    /** Parsed as selectRows parses URL filters; null when there are none. */
    private static @Nullable SimpleFilter getFilter(FilterSuggestionsRequest request, Container container, User user)
    {
        if (request.filters() == null || request.filters().isEmpty())
            return null;

        URLHelper url = new URLHelper(false);
        for (RequestFilter requestFilter : request.filters())
        {
            if (requestFilter == null || StringUtils.isBlank(requestFilter.fieldKey()) || CompareType.getByURLKey(requestFilter.op()) == null)
                throw new ApiUsageException("Invalid filter: " + requestFilter);
            url.addParameter(FILTER_REGION + "." + requestFilter.fieldKey() + SimpleFilter.SEPARATOR_CHAR + requestFilter.op(), Objects.toString(requestFilter.value(), ""));
        }

        SimpleFilter filter = new SimpleFilter();
        try
        {
            filter.addUrlFilters(url, FILTER_REGION, List.of(), user, container);
        }
        catch (ConversionException e)
        {
            throw new ApiUsageException(e.getMessage(), e);
        }
        return filter;
    }

    /** Whether each filtered column, and each lookup it's reached through, passes the checks candidate columns' data facts must pass. */
    private static boolean isReadable(TableInfo table, SimpleFilter filter)
    {
        Set<FieldKey> fieldKeys = new HashSet<>();
        for (FieldKey fieldKey : filter.getAllFieldKeys())
        {
            // A search of all columns names none
            if (fieldKey.getParent() == null && ALL_COLUMNS.equals(fieldKey.getName()))
                continue;
            for (FieldKey key = fieldKey; key != null; key = key.getParent())
                fieldKeys.add(key);
        }

        Map<FieldKey, ColumnInfo> resolved = fieldKeys.isEmpty() ? Map.of() : QueryService.get().getColumns(table, fieldKeys);
        PHI maxAllowedPhi = table.getUserMaxAllowedPhiLevel();
        for (FieldKey fieldKey : fieldKeys)
        {
            ColumnInfo column = resolved.get(fieldKey);
            if (column == null)
                throw new ApiUsageException("Unknown filter column: " + fieldKey);
            if (!FilterSuggestionColumns.isAllowed(column, maxAllowedPhi) || ColumnFactsCache.requiresAuditLogging(column))
                return false;
        }
        return true;
    }

    private static @Nullable ContainerFilter getContainerFilter(FilterSuggestionsRequest request, Container container, User user)
    {
        if (StringUtils.isBlank(request.containerFilter()))
            return null;
        ContainerFilter.Type type = ContainerFilter.getType(request.containerFilter());
        if (type == null)
            throw new ApiUsageException("'containerFilter' parameter is not valid");
        return type.create(container, user);
    }

    private static @Nullable CustomView getView(UserSchema schema, FilterSuggestionsRequest request, User user)
    {
        QueryDefinition queryDef = schema.getQueryDef(request.queryName());
        if (queryDef == null)
            queryDef = schema.getQueryDefForTable(request.queryName());
        return queryDef == null ? null : queryDef.getCustomView(user, null, StringUtils.trimToNull(request.viewName()));
    }

    private static int getMaxSuggestions(FilterSuggestionsRequest request)
    {
        Integer max = request.maxSuggestions();
        if (max == null || max < 1)
            return FilterSuggestionRanker.DEFAULT_MAX_SUGGESTIONS;
        return Math.min(max, MAX_SUGGESTIONS_LIMIT);
    }
}
