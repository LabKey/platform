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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.suggestions.FilterSuggestion;
import org.labkey.api.query.suggestions.FilterSuggestionContext;
import org.labkey.api.query.suggestions.SuggestionColumn;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HashHelpers;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fills the data-derived facts of candidate columns (ranges, value lists, value shapes) from a cache. Missing entries
 * load on a small background pool; a request waits for them only up to its budget, and late results still fill the
 * cache for the next keystroke.
 * <p>
 * Tables with a change token share entries across users, since their row visibility depends on containers alone; all
 * others are cached per user for a few minutes. Either way the key includes the resolved container scope.
 */
public class ColumnFactsCache
{
    private static final Logger LOG = LogHelper.getLogger(ColumnFactsCache.class, "Grid search suggestion column facts");

    public static final int DISTINCT_VALUE_LIMIT = 500;
    public static final long DEFAULT_BUDGET_MS = 250;
    // A data change moves the token, so these entries only need to age out
    static final long TOKEN_TTL = CacheManager.DAY;
    static final long NO_TOKEN_TTL = 5 * CacheManager.MINUTE;
    private static final int MAX_CONCURRENT_LOADS = 4;
    // Beyond this, bursts of keystrokes across tables return what's cached rather than queueing more scans
    private static final int MAX_PENDING_LOADS = 100;

    private static final ColumnFactsCache INSTANCE = new ColumnFactsCache(
        CacheManager.getStringKeyCache(CacheManager.DEFAULT_CACHE_SIZE, TOKEN_TTL, "Grid search suggestion facts"),
        new JobRunner("Grid search suggestions", MAX_CONCURRENT_LOADS),
        new ColumnFactsLoader()
    );

    /** Reads facts from the database; each call runs on a background thread. */
    public interface Source
    {
        /** @return stats keyed by each request's column fieldKey; a missing entry is recorded as a failure */
        @NotNull Map<FieldKey, ColumnFact.Stats> loadStats(@NotNull TableInfo table, @NotNull Map<String, Object> parameters, @NotNull List<StatsRequest> requests);

        @NotNull ColumnFact.Values loadValues(@NotNull TableInfo table, @NotNull ColumnInfo column, @NotNull SuggestionColumn.Type type, @NotNull Map<String, Object> parameters);

        @NotNull Map<FieldKey, ColumnFact.Shapes> loadShapes(@NotNull TableInfo table, @NotNull Collection<ColumnInfo> columns, @NotNull Map<String, Object> parameters);
    }

    /** @param range MIN and MAX; distinctCount COUNT(DISTINCT) */
    public record StatsRequest(@NotNull ColumnInfo column, boolean range, boolean distinctCount) {}

    private enum Kind { RANGE, INT, STRING, LOOKUP }

    /**
     * A table read in one container scope with one set of parameters.
     * @param owner the change token, or the user when there is none
     */
    record Scope(TableInfo table, Map<String, Object> parameters, String name, String scopeHash, String owner, long ttl)
    {
        static Scope create(TableInfo table, ContainerFilter containerFilter, Container container, User user, @Nullable String token, @Nullable Map<String, Object> parameters)
        {
            Collection<GUID> ids = containerFilter.getIds();
            String containerIds = ids == null ? "*" : ids.stream().map(GUID::toString).sorted().collect(Collectors.joining(","));
            Map<String, Object> sortedParameters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            if (parameters != null)
                sortedParameters.putAll(parameters);
            String scopeHash = HashHelpers.hash(container.getId() + "|" + containerIds + "|" + sortedParameters);
            String owner = token != null ? "t:" + token : "u:" + user.getUserId() + user.getPermissionsContext().getCacheKey();
            String name = (table.getPublicSchemaName() + "/" + table.getPublicName()).toLowerCase(Locale.ROOT);
            return new Scope(table, Collections.unmodifiableMap(sortedParameters), name, scopeHash, owner, token != null ? TOKEN_TTL : NO_TOKEN_TTL);
        }

        String key(String kind, String fieldKey, @Nullable String columnToken)
        {
            return name + "|" + kind + ":" + fieldKey.toLowerCase(Locale.ROOT) + "|" + scopeHash + "|" + owner + (columnToken == null ? "" : "+" + columnToken);
        }
    }

    /** @param lookupScope and lookupDisplay locate a lookup's values on its target table */
    private record Plan(SuggestionColumn column, ColumnInfo info, Kind kind, Scope scope, @Nullable Scope lookupScope, @Nullable ColumnInfo lookupDisplay)
    {
        String statsKey()
        {
            return scope.key("stats", column.getFieldKey(), column.getChangeToken());
        }

        String valuesKey()
        {
            if (lookupScope != null && lookupDisplay != null)
                return lookupScope.key("values", lookupDisplay.getFieldKey().toString(), null);
            return scope.key("values", column.getFieldKey(), column.getChangeToken());
        }

        String shapesKey()
        {
            return scope.key("shapes", column.getFieldKey(), column.getChangeToken());
        }
    }

    private final Cache<String, ColumnFact> _cache;
    private final Executor _executor;
    private final Source _source;
    private final Map<String, CompletableFuture<Void>> _inFlight = new ConcurrentHashMap<>();
    private final AtomicInteger _pending = new AtomicInteger();

    ColumnFactsCache(Cache<String, ColumnFact> cache, Executor executor, Source source)
    {
        _cache = cache;
        _executor = executor;
        _source = source;
    }

    public static ColumnFactsCache get()
    {
        return INSTANCE;
    }

    /**
     * Sets cached facts on the columns, loading missing ones and waiting for them up to the budget.
     * @param tokens each table's change token, or null when its facts must not be shared across users
     * @return false when some fact wasn't available within the budget
     */
    public boolean fill(@NotNull FilterSuggestionContext context, @Nullable Map<String, Object> parameters, @NotNull FilterSuggestionColumns.Described described,
                        @NotNull Function<TableInfo, String> tokens, long budgetMs)
    {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs);
        Object environment = QueryService.get().cloneEnvironment();
        TableInfo table = context.getTable();
        Map<String, Object> declaredParameters = getDeclaredParameters(table, parameters);
        // Tokens are read before the data they guard, so a race can only pair newer data with an older token
        String token = tokens.apply(table);
        Scope scope = Scope.create(table, context.getContainerFilter(), context.getContainer(), context.getUser(), token, declaredParameters);
        // The table's token doesn't cover data reached through a lookup, which can also differ per user
        Scope userScope = token == null ? scope : Scope.create(table, context.getContainerFilter(), context.getContainer(), context.getUser(), null, declaredParameters);

        List<Plan> plans = new ArrayList<>();
        for (SuggestionColumn column : described.columns())
        {
            ColumnInfo info = column.getColumnInfo();
            Scope columnScope = info != null && info.getFieldKey().getParent() == null ? scope : userScope;
            Plan plan = plan(context.getUser(), columnScope, column, described.lookups().get(column), tokens);
            if (plan != null)
                plans.add(plan);
        }
        if (plans.isEmpty())
            return true;

        List<CompletableFuture<Void>> loads = new ArrayList<>();
        // Columns with their own token get their own stats pass, so their entries follow that token
        record StatsGroup(Scope scope, @Nullable String columnToken) {}
        Map<StatsGroup, List<Plan>> groups = plans.stream()
            .filter(plan -> plan.kind() != Kind.LOOKUP)
            .collect(Collectors.groupingBy(plan -> new StatsGroup(plan.scope(), plan.column().getChangeToken()), LinkedHashMap::new, Collectors.toList()));
        groups.forEach((group, groupPlans) ->
            loads.add(loadStats(group.scope(), groupPlans, environment).thenCompose(_ -> loadValuesOrShapes(group.scope(), groupPlans, environment))));
        for (Plan plan : plans)
            if (plan.kind() == Kind.LOOKUP)
                loads.add(loadLookup(plan, environment));

        waitUntil(CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)), deadline);

        boolean complete = true;
        for (Plan plan : plans)
            complete &= apply(plan);
        return complete;
    }

    private @Nullable Plan plan(User user, Scope scope, SuggestionColumn column, @Nullable ColumnInfo lookup, Function<TableInfo, String> tokens)
    {
        ColumnInfo info = column.getColumnInfo();
        // Shared cached values would bypass the per-user audit log, so audit-logged columns keep their metadata-only facts
        if (info == null || column.isKey() || column.getValues() != null || requiresAuditLogging(info))
            return null;

        if (lookup != null)
            return planLookup(user, scope, column, info, lookup, tokens);

        return switch (column.getType())
        {
            case DATE, FLOAT -> new Plan(column, info, Kind.RANGE, scope, null, null);
            case INT -> new Plan(column, info, Kind.INT, scope, null, null);
            case STRING -> new Plan(column, info, Kind.STRING, scope, null, null);
            case BOOLEAN -> null;
        };
    }

    private @Nullable Plan planLookup(User user, Scope scope, SuggestionColumn column, ColumnInfo info, ColumnInfo lookup, Function<TableInfo, String> tokens)
    {
        try
        {
            TableInfo target = lookup.getFk() == null ? null : lookup.getFk().getLookupTableInfo();
            UserSchema targetSchema = target == null ? null : target.getUserSchema();
            FieldKey displayKey = getRelativeFieldKey(info.getFieldKey(), lookup.getFieldKey());
            if (targetSchema == null || displayKey == null || !target.hasPermission(user, ReadPermission.class))
                return null;

            ColumnInfo display = QueryService.get().getColumns(target, List.of(displayKey)).get(displayKey);
            if (display == null || !FilterSuggestionColumns.isAllowed(display, target.getUserMaxAllowedPhiLevel()) || requiresAuditLogging(display))
                return null;

            Container targetContainer = targetSchema.getContainer();
            ContainerFilter containerFilter = Objects.requireNonNullElseGet(target.getContainerFilter(), () -> ContainerFilter.current(targetContainer, user));
            Scope lookupScope = Scope.create(target, containerFilter, targetContainer, user, tokens.apply(target), null);
            return new Plan(column, info, Kind.LOOKUP, scope, lookupScope, display);
        }
        catch (RuntimeException e)
        {
            LOG.debug("Unable to resolve the lookup target of {}", column.getFieldKey(), e);
            return null;
        }
    }

    private static @Nullable FieldKey getRelativeFieldKey(FieldKey fieldKey, FieldKey parent)
    {
        if (fieldKey.size() <= parent.size() || !fieldKey.startsWith(parent))
            return null;
        List<String> parts = fieldKey.getParts();
        return FieldKey.fromParts(parts.subList(parent.size(), parts.size()));
    }

    /** Only parameters the query declares reach the key, so made-up ones can't force fresh scans. */
    private static Map<String, Object> getDeclaredParameters(TableInfo table, @Nullable Map<String, Object> parameters)
    {
        if (parameters == null || parameters.isEmpty())
            return Map.of();
        Map<String, Object> caseInsensitive = new CaseInsensitiveHashMap<>(parameters);
        Map<String, Object> declared = new HashMap<>();
        for (QueryService.ParameterDecl parameter : table.getNamedParameters())
        {
            if (caseInsensitive.containsKey(parameter.getName()))
                declared.put(parameter.getName(), caseInsensitive.get(parameter.getName()));
        }
        return declared;
    }

    static boolean requiresAuditLogging(ColumnInfo column)
    {
        ColumnLogging logging = column.getColumnLogging();
        return logging != null && (logging.shouldLogName() || logging.getException() != null || !logging.getDataLoggingColumns().isEmpty());
    }

    private CompletableFuture<Void> loadStats(Scope scope, List<Plan> group, Object environment)
    {
        Map<String, Plan> byKey = new LinkedHashMap<>();
        group.forEach(plan -> byKey.put(plan.statsKey(), plan));
        return ensure(byKey.keySet(), scope.ttl(), environment, scope.name(), keys -> {
            List<Plan> missing = keys.stream().map(byKey::get).toList();
            List<StatsRequest> requests = missing.stream()
                .map(plan -> new StatsRequest(plan.info(), plan.kind() != Kind.STRING, plan.kind() != Kind.RANGE))
                .toList();
            Map<FieldKey, ColumnFact.Stats> stats = _source.loadStats(scope.table(), scope.parameters(), requests);
            Map<String, ColumnFact> facts = new HashMap<>();
            missing.forEach(plan -> {
                ColumnFact.Stats columnStats = stats.get(plan.info().getFieldKey());
                if (columnStats != null)
                    facts.put(plan.statsKey(), columnStats);
            });
            return facts;
        });
    }

    private CompletableFuture<Void> loadValuesOrShapes(Scope scope, List<Plan> group, Object environment)
    {
        List<CompletableFuture<Void>> loads = new ArrayList<>();
        List<Plan> needShapes = new ArrayList<>();
        for (Plan plan : group)
        {
            if (!(_cache.get(plan.statsKey()) instanceof ColumnFact.Stats stats) || stats.distinctCount() == null)
                continue;
            if (stats.distinctCount() <= DISTINCT_VALUE_LIMIT)
                loads.add(loadValues(plan, scope, environment));
            else if (plan.kind() == Kind.STRING)
                needShapes.add(plan);
        }
        loads.add(loadShapes(scope, needShapes, environment));
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> loadValues(Plan plan, Scope scope, Object environment)
    {
        String key = plan.valuesKey();
        ColumnInfo column = plan.lookupDisplay() != null ? plan.lookupDisplay() : plan.info();
        return ensure(List.of(key), scope.ttl(), environment, scope.name(),
            _ -> Map.of(key, _source.loadValues(scope.table(), column, plan.column().getType(), scope.parameters())));
    }

    private CompletableFuture<Void> loadShapes(Scope scope, List<Plan> plans, Object environment)
    {
        if (plans.isEmpty())
            return CompletableFuture.completedFuture(null);

        Map<String, Plan> byKey = new LinkedHashMap<>();
        plans.forEach(plan -> byKey.put(plan.shapesKey(), plan));
        return ensure(byKey.keySet(), scope.ttl(), environment, scope.name(), keys -> {
            List<Plan> missing = keys.stream().map(byKey::get).toList();
            Map<FieldKey, ColumnFact.Shapes> shapes = _source.loadShapes(scope.table(), missing.stream().map(Plan::info).toList(), scope.parameters());
            Map<String, ColumnFact> facts = new HashMap<>();
            missing.forEach(plan -> {
                ColumnFact.Shapes columnShapes = shapes.get(plan.info().getFieldKey());
                if (columnShapes != null)
                    facts.put(plan.shapesKey(), columnShapes);
            });
            return facts;
        });
    }

    private CompletableFuture<Void> loadLookup(Plan plan, Object environment)
    {
        Scope lookupScope = Objects.requireNonNull(plan.lookupScope());
        return loadValues(plan, lookupScope, environment).thenCompose(_ ->
            _cache.get(plan.valuesKey()) instanceof ColumnFact.Values values && values.isOverflow()
                ? loadShapes(plan.scope(), List.of(plan), environment)
                : CompletableFuture.completedFuture(null));
    }

    /**
     * Starts one background load for the keys that are neither cached nor already loading.
     * @return completes once every key has been loaded or failed, or at once if the load couldn't be queued
     */
    private CompletableFuture<Void> ensure(Collection<String> keys, long ttl, Object environment, String description, Function<Set<String>, Map<String, ColumnFact>> load)
    {
        CompletableFuture<Void> future = new CompletableFuture<>();
        List<CompletableFuture<Void>> waits = new ArrayList<>();
        Set<String> claimed = new LinkedHashSet<>();
        for (String key : keys)
        {
            if (_cache.get(key) != null)
                continue;
            CompletableFuture<Void> existing = _inFlight.putIfAbsent(key, future);
            if (existing != null)
                waits.add(existing);
            else if (_cache.get(key) != null)
                _inFlight.remove(key, future); // Finished between the two checks
            else
                claimed.add(key);
        }

        if (!claimed.isEmpty())
        {
            if (submit(claimed, ttl, environment, description, load, future))
            {
                waits.add(future);
            }
            else
            {
                claimed.forEach(key -> _inFlight.remove(key, future));
                future.complete(null);
            }
        }
        return CompletableFuture.allOf(waits.toArray(CompletableFuture[]::new));
    }

    private boolean submit(Set<String> keys, long ttl, Object environment, String description, Function<Set<String>, Map<String, ColumnFact>> load, CompletableFuture<Void> future)
    {
        if (_pending.incrementAndGet() > MAX_PENDING_LOADS)
        {
            _pending.decrementAndGet();
            return false;
        }

        Runnable task = () -> {
            try
            {
                QueryService.get().copyEnvironment(environment);
                Map<String, ColumnFact> facts;
                ColumnFact failure;
                try
                {
                    facts = load.apply(keys);
                    failure = new ColumnFact.Failed("Not returned by the load");
                }
                catch (RuntimeException e)
                {
                    if (e instanceof QueryService.NamedParameterNotProvided)
                        LOG.debug("Unable to load grid search facts for {}", description, e);
                    else
                        LOG.warn("Unable to load grid search facts for {}: {}", description, e.getMessage());
                    facts = Map.of();
                    failure = new ColumnFact.Failed(Objects.toString(e.getMessage(), e.getClass().getName()));
                }
                for (String key : keys)
                {
                    ColumnFact fact = facts.get(key);
                    if (fact != null)
                        _cache.put(key, fact, ttl);
                    else
                        _cache.put(key, failure, Math.min(ttl, NO_TOKEN_TTL));
                }
            }
            finally
            {
                keys.forEach(key -> _inFlight.remove(key, future));
                // Completing runs the dependent loads' submissions, which must count as pending before this one stops
                future.complete(null);
                _pending.decrementAndGet();
                QueryService.get().clearEnvironment();
                DbScope.finishedWithThread();
            }
        };

        try
        {
            _executor.execute(task);
            return true;
        }
        catch (RejectedExecutionException e)
        {
            _pending.decrementAndGet();
            return false;
        }
    }

    private static void waitUntil(CompletableFuture<Void> loads, long deadline)
    {
        try
        {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0)
                loads.get(remaining, TimeUnit.NANOSECONDS);
        }
        catch (TimeoutException | ExecutionException ignored)
        {
            // Unfinished facts are left out, and failures are cached as such
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    /** @return false when a fact the column needs hasn't loaded yet */
    private boolean apply(Plan plan)
    {
        SuggestionColumn column = plan.column();
        if (plan.kind() == Kind.LOOKUP)
        {
            ColumnFact values = _cache.get(plan.valuesKey());
            if (values instanceof ColumnFact.Values v && v.isOverflow())
                return applyShapes(plan);
            return applyValues(plan);
        }

        ColumnFact stats = _cache.get(plan.statsKey());
        if (plan.kind() != Kind.STRING)
        {
            column.setRangeUnknown(stats instanceof ColumnFact.Failed || (stats instanceof ColumnFact.Stats s && s.rangeUnknown()));
            if (stats instanceof ColumnFact.Stats s)
                column.setRange(s.min(), s.max());
        }
        if (!(stats instanceof ColumnFact.Stats s) || s.distinctCount() == null)
            return stats != null;
        if (s.distinctCount() <= DISTINCT_VALUE_LIMIT)
            return applyValues(plan);
        return plan.kind() != Kind.STRING || applyShapes(plan);
    }

    private boolean applyValues(Plan plan)
    {
        ColumnFact values = _cache.get(plan.valuesKey());
        if (values instanceof ColumnFact.Values v && !v.isOverflow())
            plan.column().setValues(v.values());
        return values != null;
    }

    private boolean applyShapes(Plan plan)
    {
        ColumnFact shapes = _cache.get(plan.shapesKey());
        if (shapes instanceof ColumnFact.Shapes s)
            plan.column().setShapes(s.shapes());
        return shapes != null;
    }

    /** For tests: the cached fact a request would read, or null. */
    @Nullable ColumnFact peek(@NotNull FilterSuggestionContext context, @Nullable String token, @NotNull String kind, @NotNull String fieldKey)
    {
        Scope scope = Scope.create(context.getTable(), context.getContainerFilter(), context.getContainer(), context.getUser(), token, null);
        return _cache.get(scope.key(kind, fieldKey, null));
    }

    /** For tests: waits for queued and running loads, including the ones they start. */
    boolean awaitIdle(long timeoutMs) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (_pending.get() > 0 || !_inFlight.isEmpty())
        {
            if (System.currentTimeMillis() > deadline)
                return false;
            Thread.sleep(20);
        }
        return true;
    }

    public static class TestCase extends Assert
    {
        private static final String SAMPLE_TYPE = "SuggestionFacts";
        private static final int ROWS = 600;
        private static final List<String> FIELD_KEYS = List.of("color", "thawCount", "concentration", "collected", "subject");

        private static User _user;
        private static Container _c;

        private Cache<String, ColumnFact> _testCache;
        private ExecutorService _executor;

        @BeforeClass
        public static void setup() throws Exception
        {
            JunitUtil.deleteTestContainer();
            _c = JunitUtil.getTestContainer();
            _user = TestContext.get().getUser();

            List<GWTPropertyDescriptor> props = List.of(
                new GWTPropertyDescriptor("name", PropertyType.STRING.getTypeUri()),
                new GWTPropertyDescriptor("color", PropertyType.STRING.getTypeUri()),
                new GWTPropertyDescriptor("thawCount", PropertyType.INTEGER.getTypeUri()),
                new GWTPropertyDescriptor("concentration", PropertyType.DOUBLE.getTypeUri()),
                new GWTPropertyDescriptor("collected", PropertyType.DATE_TIME.getTypeUri()),
                new GWTPropertyDescriptor("subject", PropertyType.STRING.getTypeUri())
            );
            ExpSampleType sampleType = SampleTypeService.get().createSampleType(_c, _user, SAMPLE_TYPE, null, props, List.of(), -1, -1, -1, -1, null, null, null, null, null, null);

            List<String> colors = List.of("red", "green", "blue");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < ROWS; i++)
            {
                Map<String, Object> row = new CaseInsensitiveHashMap<>();
                row.put("name", "S-" + i);
                row.put("color", colors.get(i % colors.size()));
                row.put("thawCount", i % 5);
                row.put("concentration", i * 1.5);
                row.put("collected", LocalDate.of(2023, 1, 1).plusDays(i % 365).toString());
                row.put("subject", String.format("SUBJ-%06d", i));
                rows.add(row);
            }
            BatchValidationException errors = new BatchValidationException();
            Objects.requireNonNull(getTable(sampleType.getName()).getUpdateService()).insertRows(_user, _c, rows, errors, null, null);
            if (errors.hasErrors())
                throw errors;
        }

        @AfterClass
        public static void tearDown()
        {
            JunitUtil.deleteTestContainer();
        }

        @Before
        public void createCache()
        {
            _testCache = CacheManager.getTemporaryCache(1000, TOKEN_TTL, "Grid search suggestion facts test", null);
            _executor = Executors.newFixedThreadPool(2);
        }

        @After
        public void closeCache()
        {
            _executor.shutdownNow();
            _testCache.close();
        }

        private static TableInfo getTable(String name)
        {
            return Objects.requireNonNull(QueryService.get().getUserSchema(_user, _c, SamplesSchema.SCHEMA_NAME).getTable(name));
        }

        private static FilterSuggestionContext context(TableInfo table, User user)
        {
            return new FilterSuggestionContext()
            {
                @Override
                public @NotNull TableInfo getTable()
                {
                    return table;
                }

                @Override
                public @NotNull ContainerFilter getContainerFilter()
                {
                    // The table's filter for every user, so only the user can differ between contexts
                    return Objects.requireNonNullElseGet(table.getContainerFilter(), () -> ContainerFilter.current(_c, _user));
                }

                @Override
                public @NotNull Container getContainer()
                {
                    return _c;
                }

                @Override
                public @NotNull User getUser()
                {
                    return user;
                }
            };
        }

        private static Map<String, SuggestionColumn> byFieldKey(FilterSuggestionColumns.Described described)
        {
            Map<String, SuggestionColumn> columns = new CaseInsensitiveHashMap<>();
            described.columns().forEach(column -> columns.put(column.getFieldKey(), column));
            return columns;
        }

        /** Counts calls and optionally delays each one. */
        private static class TestSource implements Source
        {
            private final Source _delegate = new ColumnFactsLoader();
            private final long _delayMs;
            final AtomicInteger _calls = new AtomicInteger();

            TestSource(long delayMs)
            {
                _delayMs = delayMs;
            }

            private void call()
            {
                _calls.incrementAndGet();
                try
                {
                    Thread.sleep(_delayMs);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public @NotNull Map<FieldKey, ColumnFact.Stats> loadStats(@NotNull TableInfo table, @NotNull Map<String, Object> parameters, @NotNull List<StatsRequest> requests)
            {
                call();
                return _delegate.loadStats(table, parameters, requests);
            }

            @Override
            public @NotNull ColumnFact.Values loadValues(@NotNull TableInfo table, @NotNull ColumnInfo column, @NotNull SuggestionColumn.Type type, @NotNull Map<String, Object> parameters)
            {
                call();
                return _delegate.loadValues(table, column, type, parameters);
            }

            @Override
            public @NotNull Map<FieldKey, ColumnFact.Shapes> loadShapes(@NotNull TableInfo table, @NotNull Collection<ColumnInfo> columns, @NotNull Map<String, Object> parameters)
            {
                call();
                return _delegate.loadShapes(table, columns, parameters);
            }
        }

        @Test
        public void testFactsAndCacheHit()
        {
            TestSource source = new TestSource(0);
            ColumnFactsCache cache = new ColumnFactsCache(_testCache, _executor, source);
            TableInfo table = getTable(SAMPLE_TYPE);
            FilterSuggestionColumns.Described described = FilterSuggestionColumns.describe(table, null, FIELD_KEYS);

            assertTrue(cache.fill(context(table, _user), null, described, _ -> "token-1", 30_000));
            Map<String, SuggestionColumn> columns = byFieldKey(described);

            assertEquals(Set.of("red", "green", "blue"), Set.copyOf(Objects.requireNonNull(columns.get("color").getValues())));
            assertNull(columns.get("color").getShapes());

            SuggestionColumn count = columns.get("thawCount");
            assertEquals(Set.of("0", "1", "2", "3", "4"), Set.copyOf(Objects.requireNonNull(count.getValues())));
            assertEquals(0, new BigDecimal("0").compareTo((BigDecimal) count.getMin()));
            assertEquals(0, new BigDecimal("4").compareTo((BigDecimal) count.getMax()));
            assertFalse(count.isRangeUnknown());

            SuggestionColumn amount = columns.get("concentration");
            assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) amount.getMin()));
            assertEquals(0, new BigDecimal("898.5").compareTo((BigDecimal) amount.getMax()));
            assertNull(amount.getValues());

            SuggestionColumn when = columns.get("collected");
            assertEquals(LocalDate.of(2023, 1, 1), when.getMin());
            assertEquals(LocalDate.of(2023, 12, 31), when.getMax());

            SuggestionColumn subject = columns.get("subject");
            assertNull(subject.getValues());
            assertEquals(List.of("AAAA-999999"), subject.getShapes());

            int calls = source._calls.get();
            FilterSuggestionColumns.Described again = FilterSuggestionColumns.describe(table, null, FIELD_KEYS);
            assertTrue("Cached facts should need no wait", cache.fill(context(table, _user), null, again, _ -> "token-1", 0));
            assertEquals(calls, source._calls.get());
            assertEquals(List.of("AAAA-999999"), byFieldKey(again).get("subject").getShapes());
        }

        @Test
        public void testSharingFollowsToken()
        {
            ColumnFactsCache cache = new ColumnFactsCache(_testCache, _executor, new TestSource(0));
            TableInfo table = getTable(SAMPLE_TYPE);
            User other = UserManager.getGuestUser();

            assertTrue(cache.fill(context(table, _user), null, FilterSuggestionColumns.describe(table, null, List.of("color")), _ -> null, 30_000));
            assertNotNull(cache.peek(context(table, _user), null, "values", "color"));
            assertNull("Facts of a table without a token must not cross users", cache.peek(context(table, other), null, "values", "color"));

            assertTrue(cache.fill(context(table, _user), null, FilterSuggestionColumns.describe(table, null, List.of("color")), _ -> "shared", 30_000));
            assertNotNull(cache.peek(context(table, other), "shared", "values", "color"));
            assertNull("A new token must miss", cache.peek(context(table, other), "changed", "values", "color"));
        }

        @Test
        public void testLookupDataIsNotShared()
        {
            ColumnFactsCache cache = new ColumnFactsCache(_testCache, _executor, new TestSource(0));
            TableInfo table = getTable(SAMPLE_TYPE);
            String email = "CreatedBy/Email";
            FilterSuggestionColumns.Described described = FilterSuggestionColumns.describe(table, null, List.of(email));
            assertTrue(byFieldKey(described).containsKey(email));

            assertTrue(cache.fill(context(table, _user), null, described, _ -> "shared", 30_000));
            assertNotNull(cache.peek(context(table, _user), null, "values", email));
            assertNull("The table's token doesn't cover data reached through a lookup", cache.peek(context(table, UserManager.getGuestUser()), "shared", "values", email));
        }

        @Test
        public void testUndeclaredParametersAreIgnored()
        {
            ColumnFactsCache cache = new ColumnFactsCache(_testCache, _executor, new TestSource(0));
            TableInfo table = getTable(SAMPLE_TYPE);

            assertTrue(cache.fill(context(table, _user), Map.of("bogus", 1), FilterSuggestionColumns.describe(table, null, List.of("color")), _ -> "shared", 30_000));
            assertNotNull(cache.peek(context(table, _user), "shared", "values", "color"));
        }

        @Test
        public void testAuditLoggedColumnsReadNoData()
        {
            TestSource source = new TestSource(0);
            ColumnFactsCache cache = new ColumnFactsCache(_testCache, _executor, source);
            TableInfo table = getTable(SAMPLE_TYPE);
            Map<String, SuggestionColumn> described = byFieldKey(FilterSuggestionColumns.describe(table, null, List.of("color", "concentration")));

            List<SuggestionColumn> logged = new ArrayList<>();
            for (SuggestionColumn column : described.values())
            {
                BaseColumnInfo info = new BaseColumnInfo(Objects.requireNonNull(column.getColumnInfo()));
                info.setColumnLogging(new ColumnLogging(SamplesSchema.SCHEMA_NAME, SAMPLE_TYPE, info.getFieldKey(), true, Set.of(), "test", null));
                SuggestionColumn copy = new SuggestionColumn(column.getFieldKey(), column.getCaption(), column.getType(), info);
                copy.setRangeUnknown(column.isRangeUnknown());
                logged.add(copy);
            }

            assertTrue(cache.fill(context(table, _user), null, new FilterSuggestionColumns.Described(logged, Map.of()), _ -> "token-2", 30_000));
            assertEquals(0, source._calls.get());
            for (SuggestionColumn column : logged)
            {
                assertNull(column.getValues());
                assertNull(column.getShapes());
                assertNull(column.getMin());
            }
            assertTrue(logged.stream().filter(column -> column.getType() == SuggestionColumn.Type.FLOAT).allMatch(SuggestionColumn::isRangeUnknown));
        }

        @Test
        public void testBudget() throws Exception
        {
            ColumnFactsCache cache = new ColumnFactsCache(_testCache, _executor, new TestSource(2_000));
            TableInfo table = getTable(SAMPLE_TYPE);

            long start = System.currentTimeMillis();
            FilterSuggestionColumns.Described cold = FilterSuggestionColumns.describe(table, null, FIELD_KEYS);
            assertFalse(cache.fill(context(table, _user), null, cold, _ -> "token-3", 100));
            assertTrue("Budget not honored", System.currentTimeMillis() - start < 1_500);
            assertNull(byFieldKey(cold).get("color").getValues());
            assertNull(byFieldKey(cold).get("concentration").getMin());

            assertTrue(cache.awaitIdle(60_000));
            FilterSuggestionColumns.Described warm = FilterSuggestionColumns.describe(table, null, FIELD_KEYS);
            assertTrue("Late results should fill the cache", cache.fill(context(table, _user), null, warm, _ -> "token-3", 0));
            assertNotNull(byFieldKey(warm).get("color").getValues());
        }

        @Test
        public void testService() throws Exception
        {
            FilterSuggestionService.FilterSuggestionsRequest request = new FilterSuggestionService.FilterSuggestionsRequest(
                SamplesSchema.SCHEMA_NAME, SAMPLE_TYPE, null, null, null, FIELD_KEYS, "gree", null);
            FilterSuggestionService.getSuggestions(_user, _c, request);
            assertTrue(ColumnFactsCache.get().awaitIdle(60_000));

            List<FilterSuggestion> suggestions = FilterSuggestionService.getSuggestions(_user, _c, request).suggestions();
            assertTrue(suggestions.toString(), suggestions.stream().anyMatch(s -> "color".equalsIgnoreCase(s.fieldKey()) && "green".equals(s.value())));

            FilterSuggestionService.FilterSuggestionsRequest subject = new FilterSuggestionService.FilterSuggestionsRequest(
                SamplesSchema.SCHEMA_NAME, SAMPLE_TYPE, null, null, null, FIELD_KEYS, "SUBJ-999999", null);
            suggestions = FilterSuggestionService.getSuggestions(_user, _c, subject).suggestions();
            assertTrue(suggestions.toString(), suggestions.stream().anyMatch(s -> "subject".equalsIgnoreCase(s.fieldKey()) && "shape".equals(s.source())));
        }
    }
}
