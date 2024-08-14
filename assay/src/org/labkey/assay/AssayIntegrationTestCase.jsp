/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
<%@ page import="org.apache.logging.log4j.LogManager" %>
<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.hamcrest.MatcherAssert" %>
<%@ page import="org.junit.After" %>
<%@ page import="org.junit.Before" %>
<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.assay.AssayDataCollector" %>
<%@ page import="org.labkey.api.assay.AssayDomainService" %>
<%@ page import="org.labkey.api.assay.AssayProtocolSchema" %>
<%@ page import="org.labkey.api.assay.AssayProvider" %>
<%@ page import="org.labkey.api.assay.AssayResultTable" %>
<%@ page import="org.labkey.api.assay.AssayRunCreator" %>
<%@ page import="org.labkey.api.assay.AssayService" %>
<%@ page import="org.labkey.api.assay.DefaultAssayRunCreator" %>
<%@ page import="org.labkey.api.assay.PipelineDataCollector" %>
<%@ page import="org.labkey.api.assay.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.collections.CaseInsensitiveHashMap" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.data.JdbcType" %>
<%@ page import="org.labkey.api.data.PropertyStorageSpec" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.exp.ExperimentException" %>
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.exp.OntologyObject" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.exp.api.ExpData" %>
<%@ page import="org.labkey.api.exp.api.ExpExperiment" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.exp.property.PropertyService" %>
<%@ page import="org.labkey.api.exp.query.ExpSchema" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.files.FilesAdminOptions" %>
<%@ page import="org.labkey.api.gwt.client.assay.model.GWTProtocol" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTDomain" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTPropertyDescriptor" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.query.BatchValidationException" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QueryUpdateService" %>
<%@ page import="org.labkey.api.query.ValidationException" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.FileUtil" %>
<%@ page import="org.labkey.api.util.JunitUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="org.labkey.api.view.ViewBackgroundInfo" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.assay.AssayDomainServiceImpl" %>
<%@ page import="org.labkey.assay.TsvAssayProvider" %>
<%@ page import="org.springframework.mock.web.MockMultipartHttpServletRequest" %>
<%@ page import="java.io.File" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.nio.file.Files" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="static org.labkey.api.files.FileContentService.UPLOADED_FILE" %>
<%@ page import="static org.hamcrest.CoreMatchers.hasItem" %>
<%@ page import="static org.hamcrest.CoreMatchers.not" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleType" %>
<%@ page import="org.labkey.api.exp.api.SampleTypeService" %>
<%@ page import="static java.util.Collections.emptyList" %>
<%@ page import="org.labkey.api.exp.query.SamplesSchema" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="static org.labkey.api.exp.query.SamplesSchema.SCHEMA_SAMPLES" %>
<%@ page import="org.labkey.api.pipeline.PipeRoot" %>
<%@ page import="org.jetbrains.annotations.Nullable" %>
<%@ page import="java.io.IOException" %>

<%@ page extends="org.labkey.api.jsp.JspTest.BVT" %>
<%!
    final String ASSAY_NAME = "MyAssay";
    final String SAMPLE_TYPE_NAME = "MySampleType";

    Container c;
    ViewContext context;
    ViewBackgroundInfo info;
    Logger log;
    PipeRoot pipeRoot;
    User user;

    @Before
    public void setUp()
    {
        log = LogManager.getLogger(DefaultAssayRunCreator.class);
        JunitUtil.deleteTestContainer();
        c = JunitUtil.getTestContainer();
        user = TestContext.get().getUser();

        info = new ViewBackgroundInfo(c, user, null);
        context = new ViewContext(info);
        pipeRoot = PipelineService.get().findPipelineRoot(info.getContainer());
    }

    @After
    public void tearDown()
    {
        c = null;
        context = null;
        info = null;
        pipeRoot = null;
        user = null;
    }

    private Pair<AssayProvider, ExpProtocol> createAssay(ViewContext context, boolean editableRunsAndResults, boolean includeSampleTypeLookups) throws Exception
    {
        // create assay design
        AssayDomainService assayDomainService = new AssayDomainServiceImpl(context);
        GWTProtocol assayTemplate = assayDomainService.getAssayTemplate("General");
        assayTemplate.setName(ASSAY_NAME);
        assayTemplate.setEditableRuns(true);
        assayTemplate.setEditableResults(true);
        List<GWTDomain<GWTPropertyDescriptor>> domains = assayTemplate.getDomains();

        // clear the batch domain fields
        GWTDomain<GWTPropertyDescriptor> batchDomain = domains.stream().filter(d -> "Batch Fields".equals(d.getName())).findFirst().orElseThrow();
        batchDomain.getFields(true).clear();

        // clear the run domain fields
        GWTDomain<GWTPropertyDescriptor> runDomain = domains.stream().filter(d -> "Run Fields".equals(d.getName())).findFirst().orElseThrow();
        runDomain.getFields(true).clear();

        // clear the result domain fields and add a sample lookup
        GWTDomain<GWTPropertyDescriptor> resultDomain = domains.stream().filter(d -> "Data Fields".equals(d.getName())).findFirst().orElseThrow();
        resultDomain.getFields(true).clear();

        if (editableRunsAndResults)
        {
            GWTPropertyDescriptor runProp = new GWTPropertyDescriptor("runProp", "int");
            runDomain.getFields(true).add(runProp);
            GWTPropertyDescriptor resultProp = new GWTPropertyDescriptor("resultProp", "int");
            resultDomain.getFields(true).add(resultProp);
        }

        // Lookup to exp.Materials on run domain
        GWTPropertyDescriptor runExpMaterialLookup = new GWTPropertyDescriptor("RunExpMaterialsLookup", "int");
        runExpMaterialLookup.setLookupSchema(ExpSchema.SCHEMA_NAME);
        runExpMaterialLookup.setLookupQuery(ExpSchema.TableType.Materials.name());
        runDomain.getFields(true).add(runExpMaterialLookup);

        // Lookup to exp.Materials on results domain
        GWTPropertyDescriptor resultExpMaterialLookup = new GWTPropertyDescriptor("ResultExpMaterialsLookup", "int");
        resultExpMaterialLookup.setLookupSchema(ExpSchema.SCHEMA_NAME);
        resultExpMaterialLookup.setLookupQuery(ExpSchema.TableType.Materials.name());
        resultDomain.getFields(true).add(resultExpMaterialLookup);

        if (includeSampleTypeLookups)
        {
            ExpSampleType sampleType = createSampleType();

            // Lookup to samples.<sample type name>
            GWTPropertyDescriptor sampleTypeLookup = new GWTPropertyDescriptor("SampleTypeLookup", "string");
            sampleTypeLookup.setLookupSchema(SamplesSchema.SCHEMA_NAME);
            sampleTypeLookup.setLookupQuery(sampleType.getName());
            resultDomain.getFields(true).add(sampleTypeLookup);

            // Lookup to exp.materials.<sample type name>
            GWTPropertyDescriptor expMaterialsSampleTypeLookup = new GWTPropertyDescriptor("ExpMaterialSampleTypeLookup", "int");
            expMaterialsSampleTypeLookup.setLookupSchema(ExpSchema.SCHEMA_EXP_MATERIALS.toString());
            expMaterialsSampleTypeLookup.setLookupQuery(sampleType.getName());
            resultDomain.getFields(true).add(expMaterialsSampleTypeLookup);
        }

        // create the assay
        log.info("creating assay");
        GWTProtocol savedAssayDesign = assayDomainService.saveChanges(assayTemplate, true);
        ExpProtocol assayProtocol = ExperimentService.get().getExpProtocol(savedAssayDesign.getProtocolId());
        AssayProvider provider = AssayService.get().getProvider(assayProtocol);

        return Pair.of(provider, assayProtocol);
    }

    private File createAssayDataFile(String fileContents) throws IOException
    {
        var file = FileUtil.createTempFile(getClass().getSimpleName(), ".tsv", pipeRoot.getRootPath());
        Files.writeString(file.toPath(), fileContents, StandardCharsets.UTF_8);
        return file;
    }

    // Creates an exp material that does not reside in a sample type
    private ExpMaterial createMaterial()
    {
        final String materialName = "TestMaterial";
        final String materialLsid = ExperimentService.get().generateLSID(c, ExpMaterial.class, "TestMaterial");
        ExpMaterial material = ExperimentService.get().createExpMaterial(c, materialLsid, materialName);
        material.save(user);
        return material;
    }

    private ExpSampleType createSampleType() throws Exception
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));

        return SampleTypeService.get().createSampleType(c, user, SAMPLE_TYPE_NAME, null, props, emptyList(), -1, -1, -1, -1, "S-${genId}", null);
    }

    private List<ExpMaterial> createSamples(int numSamples) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 0; i < numSamples; i++)
            rows.add(CaseInsensitiveHashMap.of());

        ExpSampleType sampleType = SampleTypeService.get().getSampleType(c, SAMPLE_TYPE_NAME);
        TableInfo table = QueryService.get().getUserSchema(user, c, SCHEMA_SAMPLES).getTable(sampleType.getName());

        var errors = new BatchValidationException();
        var insertedRows = table.getUpdateService().insertRows(user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        List<Integer> insertedRowIds = insertedRows.stream().map(row -> (Integer) row.get("RowId")).toList();
        return new ArrayList<>(ExperimentService.get().getExpMaterials(insertedRowIds));
    }

    private ExpRun assayImportFile(
        Container c,
        User user,
        AssayProvider provider,
        ExpProtocol assayProtocol,
        File file,
        boolean allowCrossRunFileInputs,
        @Nullable Map<String, Object> requestParameters
    ) throws ExperimentException, ValidationException
    {
        HttpSession session = TestContext.get().getRequest().getSession();

        // Use the AssayFileUploadForm and the PipelineDataCollector to simulate the user selecting a file from the file browser.
        PipelineDataCollector.setFileCollection(session, c, assayProtocol, List.of(Map.of(AssayDataCollector.PRIMARY_FILE, file)));

        // Use a multipart request to trigger the AssayFileWriter.savePipelineFiles() code path that copies files into the assay upload directory
        var mockRequest = new MockMultipartHttpServletRequest();
        mockRequest.setUserPrincipal(user);
        mockRequest.setSession(session);

        if (requestParameters != null)
        {
            for (var entry : requestParameters.entrySet())
                mockRequest.addParameter(entry.getKey(), entry.getValue().toString());
        }

        var mockContext = new ViewContext(mockRequest, null, null);
        mockContext.setContainer(c);
        mockContext.setUser(user);

        var uploadForm = new AssayRunUploadForm<TsvAssayProvider>();
        uploadForm.setViewContext(new ViewContext(mockRequest, null, null));
        uploadForm.setContainer(c);
        uploadForm.setUser(user);
        uploadForm.setRowId(assayProtocol.getRowId());
        uploadForm.setName("New Run2");
        uploadForm.setDataCollectorName("Pipeline");
        if (allowCrossRunFileInputs)
            uploadForm.setAllowCrossRunFileInputs(true);

        // create a new run
        AssayRunCreator runCreator = provider.getRunCreator();
        Pair<ExpExperiment, ExpRun> pair = runCreator.saveExperimentRun(uploadForm, null);
        return pair.second;
    }

    // Issue 41675: ERROR: insert or update on table "edge" violates foreign key constraint "fk_edge_to_object"
    // - imports a file into an assay
    // - sets some file properties
    // - deletes the assay run
    // - verifies the exp.data is detached from the run, but the properties haven't been deleted
    // - re-imports the same file again
    //
    // Issue 42141: assay: importing a file into assay after deleting the run creates a new exp.data row
    // - verify only a single exp.data exists for the file
    @Test
    public void testIssue41675() throws Exception
    {
        // create a custom file property
        FileContentService fileSvc = FileContentService.get();
        PropertyDescriptor someStuffProp = null;
        if (fileSvc != null)
        {
            String domainUri = fileSvc.getDomainURI(c, FilesAdminOptions.fileConfig.useCustom);
            Domain domain = PropertyService.get().createDomain(c, domainUri, "FileProps");
            domain.addProperty(new PropertyStorageSpec("SomeStuff", JdbcType.VARCHAR));
            domain.save(user);

            DomainProperty dp = domain.getPropertyByName("SomeStuff");
            someStuffProp = dp.getPropertyDescriptor();
        }

        // create the assay
        var assayPair = createAssay(context, false, false);
        var provider = assayPair.first;
        var assayProtocol = assayPair.second;

        // create a sample that will be used as an input to the run
        log.info("creating material");
        var material = createMaterial();
        final var materialName = material.getName();

        // create a file in the pipeline root to import
        var file = createAssayDataFile("ResultExpMaterialsLookup\n" + materialName + "\n");

        // import the file
        log.info("first import");
        var run = assayImportFile(c, user, provider, assayProtocol, file, false, null);

        // verify the exp.data is attached to the run
        assertEquals(1, run.getDataOutputs().size());
        final ExpData originalOutputData = run.getDataOutputs().get(0);
        assertEquals(file.getName(), originalOutputData.getName());

        final int dataRowId = originalOutputData.getRowId();
        final String dataLsid = originalOutputData.getLSID();
        final Integer dataOID = originalOutputData.getObjectId();
        final OntologyObject oo1 = OntologyManager.getOntologyObject(originalOutputData.getObjectId());
        assertNotNull(oo1);

        // set some properties that will be verified later
        originalOutputData.setComment(user, "hello world");
        if (someStuffProp != null)
            originalOutputData.setProperty(user, someStuffProp, "SomeData");

        // verify lineage
        var parents = ExperimentService.get().getParents(c, user, originalOutputData);
        assertThat(parents.second, hasItem(material));

        // delete the run
        log.info("run delete");
        run.delete(user);

        // verify the exp.data, exp.object, and the properties were not deleted
        log.info("verifying post run delete");
        ExpData data2 = ExperimentService.get().getExpData(dataRowId);
        assertNotNull(data2);
        assertEquals(dataLsid, data2.getLSID());
        assertEquals(dataOID, data2.getObjectId());
        assertEquals("hello world", data2.getComment());
        if (someStuffProp != null)
            assertEquals("SomeData", data2.getProperty(someStuffProp));

        assertNull(data2.getRunId());
        parents = ExperimentService.get().getParents(c, user, data2);
        assertThat(parents.second, not(hasItem(material)));

        OntologyObject oo2 = OntologyManager.getOntologyObject(data2.getObjectId());
        assertNotNull(oo2);
        assertEquals(oo1.getObjectId(), oo2.getObjectId());
        assertEquals(oo1.getObjectURI(), oo2.getObjectURI());
        assertEquals(oo1.getContainer(), oo2.getContainer());
        assertEquals(oo1.getOwnerObjectId(), oo2.getOwnerObjectId());

        // import the same file again
        log.info("second import");
        var run2 = assayImportFile(c, user, provider, assayProtocol, file, false, null);

        // verify the exp.data and exp.object again
        log.info("verifying second upload");

        ExpData data3 = ExperimentService.get().getExpData(dataRowId);
        assertNotNull(data3);
        assertEquals(dataLsid, data3.getLSID());
        assertEquals(dataOID, data3.getObjectId());
        assertEquals("hello world", data3.getComment());
        if (someStuffProp != null)
            assertEquals("SomeData", data3.getProperty(someStuffProp));

        // Issue 42141: assay: importing a file into assay after deleting the run creates a new exp.data row
        var dataList = ExperimentService.get().getAllExpDataByURL(data3.getDataFileUrl(), null);
        assertEquals("Expected to re-use existing exp.data instead of creating a new one:\n" +
                dataList.toString(), 1, dataList.size());

        assertNotNull(data3.getRunId());
        assertEquals(run2.getRowId(), data3.getRunId().intValue());
        parents = ExperimentService.get().getParents(c, user, data3);
        assertThat(parents.second, hasItem(material));
    }

    // Issue 43470: Cannot import the output file from a provenance run into an assay
    // - attempt to import the file into the assay again
    // - verify an exception is thrown
    // - set allowCrossRunFileInput flag, attempt to import the file into the assay again
    // - verify the import is successful and a new exp.data is created for the same file
    @Test
    public void testCrossRunFileInputs() throws Exception
    {
        // create the assay
        var assayPair = createAssay(context, false, false);
        var provider = assayPair.first;
        var assayProtocol = assayPair.second;

        // create a sample that will be used as an input to the run
        log.info("creating material");
        ExpMaterial material = createMaterial();
        final var materialName = material.getName();

        // create a file in the pipeline root to import
        var file = createAssayDataFile("ResultExpMaterialsLookup\n" + materialName + "\n");

        var firstData = ExperimentService.get().createData(c, UPLOADED_FILE, file.getName());
        firstData.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(file).toURI());
        firstData.save(user);

        // create a sample derivation run where the file is an output
        ExpRun run = ExperimentService.get().createExperimentRun(c, "external transform run");
        run.setFilePathRoot(pipeRoot.getRootPath());

        ExpProtocol protocol = ExperimentService.get().ensureSampleDerivationProtocol(user);
        run.setProtocol(protocol);

        run = ExperimentService.get().saveSimpleExperimentRun(run,
            Map.of(material, "sample"),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Map.of(firstData, "output file"),
            Collections.emptyMap(),
            info,
            null,
            false
        );

        // verify the material is an input and the file is an output
        assertTrue(run.getMaterialInputs().containsKey(material));
        MatcherAssert.assertThat(run.getDataOutputs(), hasItem(firstData));

        log.info("first assay import attempt");
        try
        {
            assayImportFile(c, user, provider, assayProtocol, file, false, null);
            fail("Expected to throw an exception");
        }
        catch (ValidationException e)
        {
            assertEquals("File '" + pipeRoot.relativePath(file) + "' has been previously imported in run '" + run.getName() + "' (" + run.getRowId() + ")",
                    e.getMessage());
        }

        log.info("second assay import attempt");
        var assayRun = assayImportFile(c, user, provider, assayProtocol, file, true, null);

        // verify the first exp.data is attached as an input and not as an output
        assertTrue(assayRun.getMaterialInputs().containsKey(material));
        assertTrue(assayRun.getDataInputs().containsKey(firstData));

        // verify there is a new exp.data attached as the assay output
        // and that it has the same dataFileUrl as the input file
        var dataOutputs = assayRun.getDataOutputs();
        assertEquals(1, dataOutputs.size());
        var assayOutputData = dataOutputs.get(0);
        assertNotEquals(firstData, assayOutputData);
        assertEquals(firstData.getDataFileUrl(), assayOutputData.getDataFileUrl());

        var dataList = ExperimentService.get().getAllExpDataByURL(firstData.getDataFileUrl(), null);
        assertEquals(assayOutputData, dataList.get(0));
        assertEquals(firstData, dataList.get(1));
        assertEquals(dataList.size(), 2);

        log.info("delete the run and verify the duplicate exp.data was also deleted");
        assayRun.delete(user);
        dataList = ExperimentService.get().getAllExpDataByURL(firstData.getDataFileUrl(), null);
        assertEquals(firstData, dataList.get(0));
        assertEquals(dataList.size(), 1);
    }

    private Map<String, Object> getRealResult(DbSchema schema, String assayResultRealTable, int resultRowId)
    {
        Map<String, Object>[] results = new SqlSelector(schema, "SELECT * FROM assayresult." + assayResultRealTable + " WHERE rowid = '" + resultRowId + "'").getMapArray();
        return results[0];
    }

    /**
     * Verify Assay Result Created/Modified fields
     * - result created/modified/by matches run's created/modified/by on initial run upload from query view
     * - provisioned result db table have created/modified/by as null on initial run upload
     * - on run edit, result created/modified/by stays unchanged from query view, and remains null in DB
     * - on result edit, result created/by stays unchanged, but result modified/by is populated in DB and differs from run
     */
    @Test
    public void testAssayResultCreatedModified() throws Exception
    {
        // create the assay with editable runs/results
        var assayPair = createAssay(context, true, false);
        var provider = assayPair.first;
        var assayProtocol = assayPair.second;

        // create a file in the pipeline root to import
        var file = createAssayDataFile("ResultProp\n" + 100 + "\n");

        // import the file
        log.info("first import");
        var run = assayImportFile(c, user, provider, assayProtocol, file, false, null);
        int runRowId = run.getRowId();

        AssayProtocolSchema schema = provider.createProtocolSchema(user, c, assayProtocol, null);
        TableInfo runsTable = schema.getTable("Runs");
        TableInfo resultsTable = schema.getTable("Data");

        Set<String> selectColumns = new HashSet<>();
        selectColumns.add("rowId");
        selectColumns.add("Created");
        selectColumns.add("Modified");
        selectColumns.add("CreatedBy");
        selectColumns.add("ModifiedBy");
        Map<String, Object> originalRunResult = new TableSelector(runsTable, selectColumns, new SimpleFilter("rowId", runRowId), null).getMap();
        FieldKey runFieldKey = new FieldKey(FieldKey.fromParts("Run"), "RowId");
        Map<String, Object> originalQueryResult = new TableSelector(resultsTable, selectColumns, new SimpleFilter(runFieldKey, runRowId), null).getMap();
        int resultRowId = (Integer) originalQueryResult.get("rowid");

        // verify results created/modified matches run's created in query table
        Object runOriginalCreated = originalRunResult.get("Created");
        Object runOriginalModified = originalRunResult.get("Modified");
        Object resultOriginalCreated = originalQueryResult.get("Created");
        Object resultOriginalModified = originalQueryResult.get("Modified");
        assertTrue(resultOriginalCreated.equals(runOriginalCreated) && resultOriginalModified.equals(runOriginalCreated));
        assertTrue(originalRunResult.get("CreatedBy").equals(originalQueryResult.get("CreatedBy")) && originalRunResult.get("ModifiedBy").equals(originalQueryResult.get("ModifiedBy")));

        // verify results created/modified is null in DB provisioned table
        TableInfo realResultsTable = ((AssayResultTable) resultsTable).getRealTable();
        Map<String, Object> dbResult = getRealResult(resultsTable.getSchema(), realResultsTable.getName(), resultRowId);
        assertTrue(dbResult.get("Created") == null && dbResult.get("Modified") == null && dbResult.get("CreatedBy") == null && dbResult.get("ModifiedBy") == null );

        // verify editing the run won't update/populate existing data's modified/modifiedby
        QueryUpdateService runsQUS = runsTable.getUpdateService();
        var updated = new CaseInsensitiveHashMap<>();
        updated.put("RunProp", 2);
        updated.put("RowId", runRowId);
        BatchValidationException errors = new BatchValidationException();
        runsQUS.updateRows(user, c, Collections.singletonList(updated), null, errors, null, null);
        // verify runs modified is changed, but created is not
        Map<String, Object> modifiedRunResults = new TableSelector(runsTable, selectColumns, new SimpleFilter("rowId", runRowId), null).getMap();
        assertTrue(modifiedRunResults.get("Created").equals(runOriginalCreated));
        assertFalse(modifiedRunResults.get("Modified").equals(runOriginalModified));

        // verify results created/modified matches run's created in query table
        Map<String, Object> queryResultAfterRunModify = new TableSelector(resultsTable, selectColumns, new SimpleFilter(runFieldKey, runRowId), null).getMap();
        assertTrue(queryResultAfterRunModify.get("Created").equals(runOriginalCreated));
        assertTrue(queryResultAfterRunModify.get("Modified").equals(runOriginalCreated));
        assertFalse(queryResultAfterRunModify.get("Modified").equals(modifiedRunResults.get("Modified")));

        // verify created/modified in provisioned result table is still not populated after run edit
        dbResult = getRealResult(resultsTable.getSchema(), realResultsTable.getName(), resultRowId);
        assertTrue(dbResult.get("Created") == null && dbResult.get("Modified") == null);

        // now edit the result
        QueryUpdateService resultsQUS = resultsTable.getUpdateService();
        updated = new CaseInsensitiveHashMap<>();
        updated.put("ResultProp", 200);
        updated.put("RowId", resultRowId);
        errors = new BatchValidationException();
        resultsQUS.updateRows(user, c, Collections.singletonList(updated), null, errors, null, null);

        // verify result created matches run's created in query table, but result modified now differs from run's created
        Map<String, Object> modifiedResults = new TableSelector(resultsTable, selectColumns, new SimpleFilter(runFieldKey, runRowId), null).getMap();
        assertTrue(modifiedResults.get("Created").equals(runOriginalCreated));
        assertFalse(modifiedResults.get("Created").equals(modifiedResults.get("Modified")));
        assertFalse(modifiedResults.get("Modified").equals(runOriginalCreated));
        assertFalse(modifiedResults.get("Modified").equals(runOriginalModified));
        assertFalse(modifiedResults.get("Modified").equals(modifiedRunResults.get("Modified")));

        // verify modified in provisioned result table no longer null after result edit
        dbResult = getRealResult(resultsTable.getSchema(), realResultsTable.getName(), resultRowId);
        assertTrue(dbResult.get("Created") == null && dbResult.get("CreatedBy") == null);
        assertFalse(dbResult.get("Modified") == null || dbResult.get("ModifiedBy") == null);
        assertTrue(dbResult.get("Modified").equals(modifiedResults.get("Modified")));
        assertTrue(dbResult.get("ModifiedBy").equals(modifiedResults.get("ModifiedBy")));
    }

    @Test
    public void testRunResultLineageUpdate() throws Exception
    {
        // Regression coverage for Issue 45594.
        // Verify run/result properties backed by lineage update when query data is updated.

        // Arrange
        var assayPair = createAssay(context, true, true);
        var provider = assayPair.first;
        var assayProtocol = assayPair.second;

        var samples = createSamples(5);
        var runSample1 = samples.get(0);
        var runSample2 = samples.get(1);
        var resultSample1 = samples.get(2);
        var resultSample2 = samples.get(3);
        var sampleLookupSample = samples.get(4);

        // create a file in the pipeline root to import
        var fileContents = String.format("ResultExpMaterialsLookup\tSampleTypeLookup\n%s\t%s\n", resultSample1.getName(), sampleLookupSample.getName());
        var file = createAssayDataFile(fileContents);

        // create a run
        var run = assayImportFile(c, user, provider, assayProtocol, file, false, Map.of("runExpMaterialsLookup", runSample1.getName()));

        // Verify pre-conditions
        assertEquals(3, run.getMaterialInputs().size());
        assertEquals("RunExpMaterialsLookup", run.getMaterialInputs().get(runSample1));
        assertEquals("ResultExpMaterialsLookup", run.getMaterialInputs().get(resultSample1));
        assertEquals("SampleTypeLookup", run.getMaterialInputs().get(sampleLookupSample));

        AssayProtocolSchema schema = provider.createProtocolSchema(user, c, assayProtocol, null);
        TableInfo runsTable = schema.getTable("Runs");
        TableInfo resultsTable = schema.getTable("Data");

        // Act
        var errors = new BatchValidationException();
        var updatedRunRow = CaseInsensitiveHashMap.of("RowId", run.getRowId(), "RunExpMaterialsLookup", runSample2.getRowId());
        runsTable.getUpdateService().updateRows(user, c, List.of((Map) updatedRunRow), null, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        var resultRow = new TableSelector(resultsTable).getMapArray()[0];
        var updatedResultRow = CaseInsensitiveHashMap.of("RowId", resultRow.get("RowId"), "ResultExpMaterialsLookup", resultSample2.getRowId());
        resultsTable.getUpdateService().updateRows(user, c, List.of((Map) updatedResultRow), null, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        // Assert
        var updatedRun = ExperimentService.get().getExpRun(run.getRowId());
        assertNotNull(updatedRun);
        assertEquals(3, updatedRun.getMaterialInputs().size());
        assertEquals("RunExpMaterialsLookup", updatedRun.getMaterialInputs().get(runSample2));
        assertEquals("ResultExpMaterialsLookup", updatedRun.getMaterialInputs().get(resultSample2));
        assertEquals("SampleTypeLookup", updatedRun.getMaterialInputs().get(sampleLookupSample));
    }
%>
