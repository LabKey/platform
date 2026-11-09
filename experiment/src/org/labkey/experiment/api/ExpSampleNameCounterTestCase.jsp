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

<%@ page import="org.jetbrains.annotations.NotNull" %>
<%@ page import="org.jetbrains.annotations.Nullable" %>
<%@ page import="org.junit.After" %>
<%@ page import="org.junit.AfterClass" %>
<%@ page import="org.junit.Before" %>
<%@ page import="org.junit.BeforeClass" %>
<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.collections.CaseInsensitiveHashMap" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.data.NameGenerator" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleType" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.exp.api.SampleTypeService" %>
<%@ page import="org.labkey.api.exp.query.SamplesSchema" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTPropertyDescriptor" %>
<%@ page import="org.labkey.api.query.BatchValidationException" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryUpdateService" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.settings.OptionalFeatureService" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.concurrent.Callable" %>
<%@ page import="java.util.concurrent.ExecutorService" %>
<%@ page import="java.util.concurrent.Executors" %>
<%@ page import="java.util.concurrent.Future" %>
<%@ page import="java.util.concurrent.TimeUnit" %>
<%@ page import="java.util.concurrent.TimeoutException" %>
<%@ page import="static java.util.Collections.emptyList" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page extends="org.labkey.api.jsp.JspTest.BVT" %>

<%!
// Backstops for sample naming counters (GH Issue 1563): :withCounter, the max-counter probe, and the project
// ${sampleCount}. Expected values describe current behavior; changes to DbSequence or NameGenerator must keep them.

private static final String PROJECT_NAME = "_testSampleNameCounter";
// Counter activation is permanent, so tests needing a project with inactive counters each get a fresh one
private static final String COLD_PROJECT_PREFIX = "_testSampleNameCounterCold";
private static final String WITH_COUNTER_BY_LAB = "${${Lab}-:withCounter}";

private Container c;
static User _user;
private static final List<Container> _coldProjects = new ArrayList<>();

@BeforeClass
public static void setUp()
{
    _user = TestContext.get().getUser();
}

@Before
public void ensureProject()
{
    c = ContainerManager.ensureContainer(PROJECT_NAME, _user);
}

@After
public void drainQueue() throws InterruptedException
{
    SearchService.get().drainQueue(SearchService.PRIORITY.crawl, 15, TimeUnit.SECONDS);
}

@AfterClass
public static void tearDown()
{
    Container project = ContainerManager.getForPath(PROJECT_NAME);
    if (project != null)
        ContainerManager.deleteAll(project, _user);
    _coldProjects.forEach(p -> ContainerManager.deleteAll(p, _user));
    _coldProjects.clear();
}

@Test
public void testProbeBootstrapsFromExistingNames() throws Exception
{
    ExpSampleType st = createSampleType("Bootstrap", "S-${genId}");
    insertNames(c, st, "P", "P-1", "P-2", "P-3", "P-4", "P-5", "p-7", "P-0009", "P-abc", "P-1-1", "P-99999999999999999999");

    // Case-insensitive prefix match; non-numeric suffixes and suffixes beyond Long.MAX_VALUE are ignored
    assertEquals(List.of("P-10", "P-11"), insertAliquots(c, st, "P", "P"));
}

@Test
public void testProbeEscapesLikeWildcards() throws Exception
{
    ExpSampleType st = createSampleType("Wildcards", "S-${genId}");
    // "QXY!-50" matches an unescaped 'q%_!-%' pattern
    insertNames(c, st, "Q%_!", "Q%_!-3", "QXY!-50");

    assertEquals(List.of("Q%_!-4"), insertAliquots(c, st, "Q%_!"));
}

@Test
public void testProbeHandlesQuotesAndQuestionMarks() throws Exception
{
    // The probe inlines its LIKE pattern as a SQL literal
    ExpSampleType st = createSampleType("Quotes", "S-${genId}");
    insertNames(c, st, "O'Ne?l\\x", "O'Ne?l\\x-2");

    assertEquals(List.of("O'Ne?l\\x-3"), insertAliquots(c, st, "O'Ne?l\\x"));
}

@Test
public void testWithCounterExpressionStartAndFormat() throws Exception
{
    ExpSampleType plain = createSampleType("LabCounter", WITH_COUNTER_BY_LAB, "Lab");
    insertNames(c, plain, "W-3");
    assertEquals(List.of("W-4", "W-5"), insertLabs(c, plain, "W", "W"));

    ExpSampleType start = createSampleType("LabCounterStart", "${${Lab}-:withCounter(1000, '000')}", "Lab");
    assertEquals(List.of("V-1000", "V-1001"), insertLabs(c, start, "V", "V"));

    ExpSampleType format = createSampleType("LabCounterFormat", "${${Lab}-:withCounter(1, '000')}", "Lab");
    insertNames(c, format, "U-007");
    assertEquals(List.of("U-008", "U-009"), insertLabs(c, format, "U", "U"));
}

@Test
public void testWithCounterPrefixIsCaseInsensitive() throws Exception
{
    ExpSampleType st = createSampleType("CaseVariants", WITH_COUNTER_BY_LAB, "Lab");

    assertEquals(List.of("abc-1"), insertLabs(c, st, "abc"));
    assertEquals(List.of("ABC-2"), insertLabs(c, st, "ABC"));
    assertEquals(List.of("Abc-3", "Abc-4"), insertLabs(c, st, "Abc", "Abc"));
}

@Test
public void testWithCounterContinuesAcrossFolders() throws Exception
{
    // Each folder keeps its own counter sequence; the cross-folder probe is what prevents duplicate names
    Container sub = ContainerManager.ensureContainer(c, "CounterSubfolder", _user);
    ExpSampleType st = createSampleType("CrossFolder", WITH_COUNTER_BY_LAB, "Lab");

    assertEquals(List.of("X-1", "X-2"), insertLabs(c, st, "X", "X"));
    assertEquals(List.of("X-3"), insertLabs(sub, st, "X"));
    assertEquals(List.of("X-4"), insertLabs(c, st, "X"));
}

@Test
public void testRolledBackInsertReusesCounterValues() throws Exception
{
    ExpSampleType counted = createSampleType("RollbackCounted", "C-${sampleCount}", "Lab");
    ExpSampleType withCounter = createSampleType("RollbackWithCounter", WITH_COUNTER_BY_LAB, "Lab");
    insertLabs(c, counted, "activate");
    long before = currentSampleCount();

    List<String> expectedWithCounter = List.of("R-1", "R-2");
    List<String> expectedCounted = List.of("C-" + (before + 3), "C-" + (before + 4));

    DbScope scope = ExperimentService.get().getSchema().getScope();
    try (DbScope.Transaction ignored = scope.ensureTransaction())
    {
        assertEquals(expectedWithCounter, insertLabs(c, withCounter, "R", "R"));
        assertEquals(expectedCounted, insertLabs(c, counted, "r", "r"));
    }
    assertEquals("Rollback must restore sampleCount", before, currentSampleCount());

    assertEquals(expectedWithCounter, insertLabs(c, withCounter, "R", "R"));
    assertEquals(expectedCounted, insertLabs(c, counted, "r", "r"));
    assertEquals(before + 4, currentSampleCount());
}

@Test
public void testSuppliedNamesAdvanceActiveSampleCount() throws Exception
{
    ExpSampleType counted = createSampleType("ActivateCount", "C-${sampleCount}", "Lab");
    ExpSampleType supplied = createSampleType("SuppliedNames", "S-${genId}");
    insertLabs(c, counted, "activate");
    long before = currentSampleCount();

    insertNames(c, supplied, "sup-1", "sup-2", "sup-3");
    assertEquals("Once active, every inserted sample draws sampleCount", before + 3, currentSampleCount());

    insertAliquots(c, supplied, "sup-1");
    assertEquals("Aliquots draw sampleCount too", before + 4, currentSampleCount());
}

@Test
public void testSampleCountContiguousAcrossTypesInOneTransaction() throws Exception
{
    ExpSampleType a = createSampleType("CountA", "A-${sampleCount}", "Lab");
    ExpSampleType b = createSampleType("CountB", "B-${sampleCount}", "Lab");
    insertLabs(c, a, "activate");
    long n = currentSampleCount();

    DbScope scope = ExperimentService.get().getSchema().getScope();
    try (DbScope.Transaction tx = scope.ensureTransaction())
    {
        assertEquals(List.of("A-" + (n + 1), "A-" + (n + 2)), insertLabs(c, a, "a", "a"));
        assertEquals(List.of("B-" + (n + 3), "B-" + (n + 4)), insertLabs(c, b, "b", "b"));
        tx.commit();
    }
    assertEquals(n + 4, currentSampleCount());
}

@Test
public void testCounterFreeImportsRunConcurrently() throws Exception
{
    Container cold = createColdProject();
    ExpSampleType first = createSampleType(cold, "FreeFirst", "F-${genId}");
    ExpSampleType second = createSampleType(cold, "FreeSecond", "G-${genId}");

    assertSecondImportWaits(false, "An import that draws no counter must not wait for the project lock",
            () -> insertNames(cold, first, "free-1"), () -> insertRows(cold, second, List.of(new CaseInsensitiveHashMap<>())));
}

@Test
public void testWithCounterImportWaitsForProjectLock() throws Exception
{
    Container cold = createColdProject();
    ExpSampleType st = createSampleType(cold, "LockParents", "L-${genId}");
    insertNames(cold, st, "lp-1", "lp-2");

    // Different prefixes share no sequence row, so only the project lock makes the second wait
    assertSecondImportWaits(true, "A :withCounter import must take the project lock",
            () -> insertAliquots(cold, st, "lp-1"), () -> insertAliquots(cold, st, "lp-2"));
    assertEquals(List.of("lp-1-2"), insertAliquots(cold, st, "lp-1"));
    assertEquals(List.of("lp-2-2"), insertAliquots(cold, st, "lp-2"));
}

@Test
public void testActiveSampleCountImportsWaitForProjectLock() throws Exception
{
    Container cold = createColdProject();
    ExpSampleType counted = createSampleType(cold, "ActiveCounted", "C-${sampleCount}", "Lab");
    ExpSampleType supplied = createSampleType(cold, "ActiveSupplied", "S-${genId}");
    insertLabs(cold, counted, "activate");

    assertSecondImportWaits(true, "Once sampleCount is active, every sample import must take the project lock",
            () -> insertNames(cold, supplied, "active-1"), () -> insertNames(cold, supplied, "active-2"));
    assertEquals(3, SampleTypeService.get().getCurrentCount(NameGenerator.EntityCounter.sampleCount, cold));
}

@Test
public void testEagerCounterLockFlag() throws Exception
{
    Container cold = createColdProject();
    ExpSampleType st = createSampleType(cold, "Eager", "E-${genId}");

    OptionalFeatureService features = OptionalFeatureService.get();
    boolean wasEnabled = features.isFeatureEnabled(NameGenerator.EXPERIMENTAL_EAGER_COUNTER_LOCK);
    features.setFeatureEnabled(NameGenerator.EXPERIMENTAL_EAGER_COUNTER_LOCK, true, _user);
    try
    {
        assertSecondImportWaits(true, "The flag must serialize imports that draw no counter",
                () -> insertNames(cold, st, "eager-1"), () -> insertNames(cold, st, "eager-2"));
    }
    finally
    {
        features.setFeatureEnabled(NameGenerator.EXPERIMENTAL_EAGER_COUNTER_LOCK, wasEnabled, _user);
    }
}

@Test
public void testOppositeOrderAliquotImportsDoNotDeadlock() throws Exception
{
    Container cold = createColdProject();
    ExpSampleType st = createSampleType(cold, "OppositeOrder", "O-${genId}");
    insertNames(cold, st, "oo-1", "oo-2");

    int rounds = 5;
    DbScope scope = ExperimentService.get().getSchema().getScope();
    try (ExecutorService executor = Executors.newFixedThreadPool(2))
    {
        for (int i = 0; i < rounds; i++)
        {
            List<Future<List<String>>> imports = new ArrayList<>();
            for (List<String> parents : List.of(List.of("oo-1", "oo-2"), List.of("oo-2", "oo-1")))
            {
                imports.add(executor.submit(() -> {
                    try (DbScope.Transaction tx = scope.ensureTransaction())
                    {
                        List<String> names = insertAliquots(cold, st, parents.toArray(String[]::new));
                        tx.commit();
                        return names;
                    }
                }));
            }
            for (Future<List<String>> f : imports)
                f.get(60, TimeUnit.SECONDS);
        }
    }

    assertEquals("Each parent's aliquots must stay contiguous", List.of("oo-1-" + (2 * rounds + 1), "oo-2-" + (2 * rounds + 1)), insertAliquots(cold, st, "oo-1", "oo-2"));
}

private Container createColdProject()
{
    Container project = ContainerManager.ensureContainer(COLD_PROJECT_PREFIX + GUID.makeHash().substring(0, 8), _user);
    _coldProjects.add(project);
    return project;
}

/** Runs second in its own transaction on another thread while first's transaction is still open */
private void assertSecondImportWaits(boolean expectWait, String message, Callable<?> first, Callable<?> second) throws Exception
{
    DbScope scope = ExperimentService.get().getSchema().getScope();
    try (ExecutorService executor = Executors.newSingleThreadExecutor())
    {
        Future<?> secondImport;
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            first.call();
            secondImport = executor.submit(() -> {
                try (DbScope.Transaction tx2 = scope.ensureTransaction())
                {
                    second.call();
                    tx2.commit();
                    return null;
                }
            });

            if (expectWait)
                assertThrows(message, TimeoutException.class, () -> secondImport.get(2, TimeUnit.SECONDS));
            else
                secondImport.get(30, TimeUnit.SECONDS);
            tx.commit();
        }
        secondImport.get(60, TimeUnit.SECONDS);
    }
}

private ExpSampleType createSampleType(String baseName, @Nullable String nameExpression, String... stringFields) throws Exception
{
    return createSampleType(c, baseName, nameExpression, stringFields);
}

private ExpSampleType createSampleType(Container container, String baseName, @Nullable String nameExpression, String... stringFields) throws Exception
{
    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    Arrays.stream(stringFields).forEach(f -> props.add(new GWTPropertyDescriptor(f, "string")));
    String name = baseName + "-" + GUID.makeHash().substring(0, 8);
    return SampleTypeService.get().createSampleType(container, _user, name, null, props, emptyList(), -1, -1, -1, -1, nameExpression, null);
}

private long currentSampleCount()
{
    return SampleTypeService.get().getCurrentCount(NameGenerator.EntityCounter.sampleCount, c);
}

private List<String> insertNames(Container container, ExpSampleType st, String... names) throws Exception
{
    return insertRows(container, st, Arrays.stream(names).<Map<String, Object>>map(n -> CaseInsensitiveHashMap.of("Name", n)).toList());
}

private List<String> insertLabs(Container container, ExpSampleType st, String... labs) throws Exception
{
    return insertRows(container, st, Arrays.stream(labs).<Map<String, Object>>map(l -> CaseInsensitiveHashMap.of("Lab", l)).toList());
}

private List<String> insertAliquots(Container container, ExpSampleType st, String... parents) throws Exception
{
    return insertRows(container, st, Arrays.stream(parents).<Map<String, Object>>map(p -> CaseInsensitiveHashMap.of("AliquotedFrom", p)).toList());
}

/** Inserts through the sample type's update service and returns the names in row order. */
private List<String> insertRows(Container container, ExpSampleType st, List<Map<String, Object>> rows) throws Exception
{
    BatchValidationException errors = new BatchValidationException();
    List<Map<String, Object>> ret = getUpdateService(container, st).insertRows(_user, container, rows, errors, null, null);
    if (errors.hasErrors())
        throw errors;
    return ret.stream().map(r -> (String) r.get("Name")).toList();
}

private @NotNull QueryUpdateService getUpdateService(Container container, ExpSampleType st)
{
    UserSchema schema = QueryService.get().getUserSchema(_user, container, SamplesSchema.SCHEMA_SAMPLES);
    assertNotNull(schema);
    QueryUpdateService qus = schema.getTableOrThrow(st.getName()).getUpdateService();
    assertNotNull("Update service cannot be null", qus);
    return qus;
}
%>
