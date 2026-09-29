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
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.suggestions.FilterSuggestion;
import org.labkey.api.query.suggestions.FilterSuggestionContext;
import org.labkey.api.query.suggestions.FilterSuggestionProvider;
import org.labkey.api.query.suggestions.SuggestionColumn;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Backs query-getFilterSuggestions.api: resolves the table, describes its candidate columns, and ranks suggestions. */
public class FilterSuggestionService
{
    private static final Logger LOG = LogHelper.getLogger(FilterSuggestionService.class, "Grid search filter suggestions");
    private static final int MAX_SUGGESTIONS_LIMIT = 100;

    /**
     * @param columns    the grid's display column fieldKeys
     * @param parameters values for a parameterized query; unused until suggestions read the table's data
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FilterSuggestionsRequest(
        String schemaName,
        String queryName,
        @Nullable String viewName,
        @Nullable String containerFilter,
        @Nullable Map<String, Object> parameters,
        @Nullable List<String> columns,
        @Nullable String term,
        @Nullable Integer maxSuggestions
    ) {}

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

        if (StringUtils.isBlank(request.schemaName()) || StringUtils.isBlank(request.queryName()))
            throw new ApiUsageException("schemaName and queryName are required.");

        UserSchema schema = QueryService.get().getUserSchema(user, container, request.schemaName());
        if (schema == null)
            throw new NotFoundException("Schema not found: " + request.schemaName());

        TableInfo table = schema.getTable(request.queryName(), getContainerFilter(request, container, user));
        if (table == null)
            throw new NotFoundException("Query not found: " + request.queryName());
        if (!table.hasPermission(user, ReadPermission.class))
            throw new UnauthorizedException();

        List<SuggestionColumn> columns = FilterSuggestionColumns.describe(table, getView(schema, request, user), request.columns());

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

        return new FilterSuggestionsResponse(FilterSuggestionRanker.rank(term, columns, getMaxSuggestions(request)), complete);
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
