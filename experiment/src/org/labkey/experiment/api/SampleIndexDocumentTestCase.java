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
package org.labkey.experiment.api;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.TestContext;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.experiment.ExpDataIterators;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;

public class SampleIndexDocumentTestCase extends Assert
{
    private static final String PROJECT_NAME = "_testSampleIndexDocument";
    private static final String PARENT_TYPE = "IndexParents";

    private static User _user;
    private static Container _project;
    private static Container _subfolder;

    @BeforeClass
    public static void setUp() throws Exception
    {
        _user = TestContext.get().getUser();
        tearDown();
        _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, _user);
        _subfolder = ContainerManager.createContainer(_project, "sub", _user);
        SampleTypeService.get().createSampleType(_project, _user, PARENT_TYPE, null, List.of(new GWTPropertyDescriptor("name", "string")), emptyList(), -1, -1, -1, -1, null, null);
        insertSamples(_project, PARENT_TYPE, List.of(CaseInsensitiveHashMap.of("name", "P1"), CaseInsensitiveHashMap.of("name", "P2")));
    }

    @After
    public void drainQueue() throws InterruptedException
    {
        // Wait for the indexer to finish working on the data we just added to help avoid deadlocks
        SearchService.get().drainQueue(SearchService.PRIORITY.crawl, 15, TimeUnit.SECONDS);
    }

    @AfterClass
    public static void tearDown()
    {
        Container project = ContainerManager.getForPath(PROJECT_NAME);
        if (project != null)
            ContainerManager.deleteAll(project, _user);
    }

    @Test
    public void testBatchMatchesOneAtATime() throws Exception
    {
        ExpSampleType st = createSampleType("IndexSamples");
        long p1 = sample(PARENT_TYPE, "P1").getRowId();
        insertSamples(_project, st.getName(), List.of(
                row("name", "S1", "TextField", "alpha", "NoteField", "first line\nsecond line", "IntField", 41,
                        "ParentRef", p1, "MaterialInputs/" + PARENT_TYPE, "P1", "Alias", "alias-one"),
                CaseInsensitiveHashMap.of("name", "S2", "TextField", "beta", "IntField", 42)
        ));
        insertSamples(_project, st.getName(), List.of(CaseInsensitiveHashMap.of("AliquotedFrom", "S1")));
        insertSamples(_subfolder, st.getName(), List.of(CaseInsensitiveHashMap.of("name", "S3", "TextField", "gamma")));

        List<Material> materials = getMaterials(st);
        assertEquals("Samples of " + st.getName(), 4, materials.size());

        Map<String, Map<String, Object>> batch = normalize(ExpMaterialImpl.createIndexDocuments(materials, new HashMap<>()));
        Map<String, Map<String, Object>> oneAtATime = normalize(materials.stream().map(m -> new ExpMaterialImpl(m).createIndexDocument(null)).filter(Objects::nonNull).toList());
        assertEquals(oneAtATime, batch);

        ExpMaterial s1 = sample(st.getName(), "S1");
        Map<String, Object> s1Doc = batch.get(s1.getDocumentId());
        assertTrue("alias in title", ((String) s1Doc.get(SearchService.PROPERTY.title.toString())).contains("alias-one"));
        assertNotNull(s1.getRun());
        assertNotNull(s1.getSourceProtocol());
        assertTrue("run name in body", ((String) s1Doc.get("body")).contains(s1.getRun().getName()));
        assertTrue("protocol name in body", ((String) s1Doc.get("body")).contains(s1.getSourceProtocol().getName()));
        assertTokens(s1Doc, SearchService.PROPERTY.identifiersMed, "alpha", "41", "P1");
        assertTokens(s1Doc, SearchService.PROPERTY.keywordsLo, "first", "second");

        ExpMaterial s3 = sample(_subfolder, st.getName(), "S3");
        Map<String, Object> s3Doc = batch.get(s3.getDocumentId());
        assertNotNull("subfolder sample indexed", s3Doc);
        assertEquals(_subfolder.getEntityId().toString(), s3Doc.get("container"));
        assertTokens(s3Doc, SearchService.PROPERTY.identifiersMed, "gamma");
    }

    @Test
    public void testDeletedSampleHasNoDocument() throws Exception
    {
        ExpSampleType st = createSampleType("IndexDeleted");
        insertSamples(_project, st.getName(), List.of(CaseInsensitiveHashMap.of("name", "D1"), CaseInsensitiveHashMap.of("name", "D2")));
        List<Material> materials = getMaterials(st);
        ExpMaterial d1 = sample(st.getName(), "D1");
        sample(st.getName(), "D2").delete(_user);

        Map<String, Map<String, Object>> docs = normalize(ExpMaterialImpl.createIndexDocuments(materials, new HashMap<>()));
        assertEquals(List.of(d1.getDocumentId()), List.copyOf(docs.keySet()));
    }

    @Test
    public void testChangeFromAnotherFolderKeepsValues() throws Exception
    {
        ExpSampleType st = createSampleType("IndexCrossFolder");
        insertSamples(_subfolder, st.getName(), List.of(CaseInsensitiveHashMap.of("name", "X1", "TextField", "delta")));
        ExpMaterial x1 = sample(_subfolder, st.getName(), "X1");

        List<WebdavResource> docs = new ArrayList<>();
        SearchService.TaskIndexingQueue queue = new SearchService.TaskIndexingQueue()
        {
            @Override
            public void addRunnable(@NotNull Consumer<SearchService.TaskIndexingQueue> r)
            {
                r.accept(this);
            }

            @Override
            public void addResource(@NotNull WebdavResource r)
            {
                docs.add(r);
            }

            @Override
            public Container getContainer()
            {
                return _project;
            }
        };
        ExpMaterialTableImpl.queueSearchIndexing(queue, _project, (ExpSampleTypeImpl) st, new ExpDataIterators.SearchIndexDataKeys(List.of(x1.getRowId()), List.of()));

        Map<String, Object> doc = normalize(docs).get(x1.getDocumentId());
        assertNotNull("X1 indexed", doc);
        assertEquals(_subfolder.getEntityId().toString(), doc.get("container"));
        assertTokens(doc, SearchService.PROPERTY.identifiersMed, "delta");
    }

    private static ExpSampleType createSampleType(String name) throws Exception
    {
        GWTPropertyDescriptor parentRef = new GWTPropertyDescriptor("ParentRef", PropertyType.INTEGER.getTypeUri());
        parentRef.setLookupSchema(SamplesSchema.SCHEMA_NAME);
        parentRef.setLookupQuery(PARENT_TYPE);
        List<GWTPropertyDescriptor> props = List.of(
                new GWTPropertyDescriptor("name", "string"),
                new GWTPropertyDescriptor("TextField", PropertyType.STRING.getTypeUri()),
                new GWTPropertyDescriptor("NoteField", PropertyType.MULTI_LINE.getTypeUri()),
                new GWTPropertyDescriptor("IntField", PropertyType.INTEGER.getTypeUri()),
                parentRef);
        return SampleTypeService.get().createSampleType(_project, _user, name, null, props, emptyList(), -1, -1, -1, -1, null, null);
    }

    private static void insertSamples(Container c, String sampleType, List<Map<String, Object>> rows) throws Exception
    {
        QueryUpdateService qus = QueryService.get().getUserSchema(_user, c, SamplesSchema.SCHEMA_SAMPLES).getTableOrThrow(sampleType).getUpdateService();
        assertNotNull(qus);
        BatchValidationException errors = new BatchValidationException();
        qus.insertRows(_user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;
    }

    private static Map<String, Object> row(Object... keysAndValues)
    {
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2)
            row.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return row;
    }

    private static ExpMaterial sample(String sampleType, String name)
    {
        return sample(_project, sampleType, name);
    }

    private static ExpMaterial sample(Container c, String sampleType, String name)
    {
        ExpMaterial m = SampleTypeService.get().getSampleType(_project, sampleType).getSample(c, name);
        assertNotNull(name, m);
        return m;
    }

    private static List<Material> getMaterials(ExpSampleType st)
    {
        return new TableSelector(ExperimentServiceImpl.get().getTinfoMaterial(), new SimpleFilter(FieldKey.fromParts("CpasType"), st.getLSID()), new Sort("RowId"))
                .getArrayList(Material.class);
    }

    private static void assertTokens(Map<String, Object> doc, SearchService.PROPERTY property, String... expected)
    {
        Object tokens = doc.get(property.toString());
        assertTrue(property + " " + tokens, tokens instanceof List<?> list && list.stream().map(String::valueOf).toList().containsAll(List.of(expected)));
    }

    /** Document id to a comparable view of the document: set-valued properties become sorted token lists, since set order isn't stable */
    private static Map<String, Map<String, Object>> normalize(List<WebdavResource> docs) throws Exception
    {
        Map<String, Map<String, Object>> result = new TreeMap<>();
        for (WebdavResource doc : docs)
        {
            Map<String, Object> view = new TreeMap<>();
            for (Map.Entry<String, ?> e : doc.getProperties().entrySet())
            {
                Object v = e.getValue();
                if (v instanceof JSONObject json)
                    v = json.toMap();
                else if (v instanceof String s && e.getKey().matches("identifiers(Hi|Med|Lo)|keywords(Hi|Med|Lo)"))
                    v = Arrays.stream(s.split("\\s+")).filter(t -> !t.isEmpty()).sorted().toList();
                view.put(e.getKey(), v);
            }
            try (InputStream in = Objects.requireNonNull(doc.getInputStream(_user)))
            {
                view.put("body", IOUtils.toString(in, StandardCharsets.UTF_8));
            }
            view.put("container", doc.getContainerId().toString());
            result.put(doc.getDocumentId(), view);
        }
        return result;
    }
}
