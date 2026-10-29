/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
<%@ page import="org.apache.commons.beanutils.ConversionException" %>
<%@ page import="org.apache.commons.collections.MapUtils" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>
<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.hamcrest.MatcherAssert" %>
<%@ page import="org.jetbrains.annotations.Nullable" %>
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
<%@ page import="org.labkey.api.data.BatchSize" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.data.JdbcType" %>
<%@ page import="org.labkey.api.data.PropertyStorageSpec" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.data.queryprofiler.QueryCounter" %>
<%@ page import="org.labkey.api.exp.ExperimentException" %>
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.exp.OntologyObject" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.exp.api.ExpData" %>
<%@ page import="org.labkey.api.exp.api.ExpExperiment" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleType" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.exp.api.ProvenanceService" %>
<%@ page import="org.labkey.api.exp.api.SampleTypeService" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.exp.property.PropertyService" %>
<%@ page import="org.labkey.api.exp.query.ExpSchema" %>
<%@ page import="org.labkey.api.exp.query.SamplesSchema" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.files.FilesAdminOptions" %>
<%@ page import="org.labkey.api.gwt.client.assay.model.GWTProtocol" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTDomain" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTPropertyDescriptor" %>
<%@ page import="org.labkey.api.pipeline.PipeRoot" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.query.BatchValidationException" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryUpdateService" %>
<%@ page import="org.labkey.api.query.ValidationException" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.FileUtil" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.JunitUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="org.labkey.api.view.ViewBackgroundInfo" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.assay.AssayDomainServiceImpl" %>
<%@ page import="org.labkey.assay.TsvAssayProvider" %>
<%@ page import="org.labkey.vfs.FileSystemLike" %>
<%@ page import="org.springframework.mock.web.MockMultipartHttpServletRequest" %>
<%@ page import="java.io.File" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="static org.labkey.api.files.FileContentService.UPLOADED_FILE" %>
<%@ page import="static org.hamcrest.CoreMatchers.hasItem" %>
<%@ page import="static org.hamcrest.CoreMatchers.not" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="static java.util.Collections.emptyList" %>
<%@ page import="java.nio.file.Files" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="static org.labkey.api.exp.query.SamplesSchema.SCHEMA_SAMPLES" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="static org.junit.Assert.assertEquals" %>
<%@ page import="static org.junit.Assert.assertNotEquals" %>

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
        return createAssay(context, editableRunsAndResults, includeSampleTypeLookups, emptyList());
    }

    private Pair<AssayProvider, ExpProtocol> createAssay(ViewContext context, boolean editableRunsAndResults, boolean includeSampleTypeLookups, List<GWTPropertyDescriptor> extraResultFields) throws Exception
    {
        // create assay design
        AssayDomainService assayDomainService = new AssayDomainServiceImpl(context.getUser(), context.getContainer());
        GWTProtocol assayTemplate = assayDomainService.getAssayTemplate("General");
        assayTemplate.setName(ASSAY_NAME);
        assayTemplate.setEditableRuns(true);
        assayTemplate.setEditableResults(true);
        List<GWTDomain<GWTPropertyDescriptor>> domains = assayTemplate.getDomains();

        List<GWTPropertyDescriptor> runFields = new ArrayList<>();
        List<GWTPropertyDescriptor> resultFields = new ArrayList<>();

        if (editableRunsAndResults)
        {
            GWTPropertyDescriptor runProp = new GWTPropertyDescriptor("runProp", "int");
            runFields.add(runProp);
            GWTPropertyDescriptor resultProp = new GWTPropertyDescriptor("resultProp", "int");
            resultFields.add(resultProp);
        }

        // Lookup to exp.Materials on run domain
        GWTPropertyDescriptor runExpMaterialLookup = new GWTPropertyDescriptor("RunExpMaterialsLookup", "int");
        runExpMaterialLookup.setLookupSchema(ExpSchema.SCHEMA_NAME);
        runExpMaterialLookup.setLookupQuery(ExpSchema.TableType.Materials.name());
        runFields.add(runExpMaterialLookup);

        // Lookup to exp.Materials on results domain
        GWTPropertyDescriptor resultExpMaterialLookup = new GWTPropertyDescriptor("ResultExpMaterialsLookup", "int");
        resultExpMaterialLookup.setLookupSchema(ExpSchema.SCHEMA_NAME);
        resultExpMaterialLookup.setLookupQuery(ExpSchema.TableType.Materials.name());
        resultFields.add(resultExpMaterialLookup);

        if (includeSampleTypeLookups)
        {
            ExpSampleType sampleType = createSampleType();

            // Lookup to samples.<sample type name>
            GWTPropertyDescriptor sampleTypeLookup = new GWTPropertyDescriptor("SampleTypeLookup", "string");
            sampleTypeLookup.setLookupSchema(SamplesSchema.SCHEMA_NAME);
            sampleTypeLookup.setLookupQuery(sampleType.getName());
            resultFields.add(sampleTypeLookup);

            // Lookup to exp.materials.<sample type name>
            GWTPropertyDescriptor expMaterialsSampleTypeLookup = new GWTPropertyDescriptor("ExpMaterialSampleTypeLookup", "int");
            expMaterialsSampleTypeLookup.setLookupSchema(ExpSchema.SCHEMA_EXP_MATERIALS.toString());
            expMaterialsSampleTypeLookup.setLookupQuery(sampleType.getName());
            resultFields.add(expMaterialsSampleTypeLookup);
        }

        resultFields.addAll(extraResultFields);

        // clear the batch domain fields
        GWTDomain<GWTPropertyDescriptor> batchDomain = domains.stream().filter(d -> "Batch Fields".equals(d.getName())).findFirst().orElseThrow();
        batchDomain.setFields(Collections.emptyList());

        // set the run domain fields
        GWTDomain<GWTPropertyDescriptor> runDomain = domains.stream().filter(d -> "Run Fields".equals(d.getName())).findFirst().orElseThrow();
        runDomain.setFields(runFields);

        // set the result domain fields
        GWTDomain<GWTPropertyDescriptor> resultDomain = domains.stream().filter(d -> "Data Fields".equals(d.getName())).findFirst().orElseThrow();
        resultDomain.setFields(resultFields);

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
        return createSampleType(SAMPLE_TYPE_NAME, "S-${genId}");
    }

    private ExpSampleType createSampleType(String sampleTypeName, String nameExpression) throws Exception
    {
        return createSampleType(c, sampleTypeName, nameExpression);
    }

    private ExpSampleType createSampleType(Container container, String sampleTypeName, String nameExpression) throws Exception
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));

        return SampleTypeService.get().createSampleType(container, user, sampleTypeName, null, props, emptyList(), -1, -1, -1, -1, nameExpression, null);
    }

    private List<ExpMaterial> createSamples(int numSamples) throws Exception
    {
        return createSamples(SAMPLE_TYPE_NAME, numSamples);
    }

    private List<ExpMaterial> createSamples(String sampleTypeName, int numSamples) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 0; i < numSamples; i++)
            rows.add(CaseInsensitiveHashMap.of());

        return insertSamples(sampleTypeName, rows);
    }

    private ExpMaterial createNamedSample(String sampleTypeName, String name) throws Exception
    {
        return insertSamples(sampleTypeName, List.of(CaseInsensitiveHashMap.of("name", name))).getFirst();
    }

    private List<ExpMaterial> insertSamples(String sampleTypeName, List<Map<String, Object>> rows) throws Exception
    {
        return insertSamples(c, sampleTypeName, rows);
    }

    private List<ExpMaterial> insertSamples(Container container, String sampleTypeName, List<Map<String, Object>> rows) throws Exception
    {
        ExpSampleType sampleType = SampleTypeService.get().getSampleType(container, sampleTypeName);
        TableInfo table = QueryService.get().getUserSchema(user, container, SCHEMA_SAMPLES).getTable(sampleType.getName());

        var errors = new BatchValidationException();
        var insertedRows = table.getUpdateService().insertRows(user, container, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        List<Long> insertedRowIds = insertedRows.stream().map(row -> MapUtils.getLong(row,"RowId")).toList();
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
        return assayImportFile(c, user, provider, assayProtocol, file, allowCrossRunFileInputs, requestParameters, true);
    }

    private ExpRun assayImportFile(
        Container c,
        User user,
        AssayProvider provider,
        ExpProtocol assayProtocol,
        File file,
        boolean allowCrossRunFileInputs,
        @Nullable Map<String, Object> requestParameters,
        boolean allowLookupByAlternateKey
    ) throws ExperimentException, ValidationException
    {
        HttpSession session = TestContext.get().getRequest().getSession();

        // Use the AssayFileUploadForm and the PipelineDataCollector to simulate the user selecting a file from the file browser.
        PipelineDataCollector.setFileCollection(session, c, assayProtocol, List.of(Map.of(AssayDataCollector.PRIMARY_FILE, FileSystemLike.wrapFile(file))));

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

        var uploadForm = new AssayRunUploadForm<TsvAssayProvider>()
        {
            @Override
            public boolean isAllowLookupByAlternateKey()
            {
                return allowLookupByAlternateKey;
            }
        };
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
        final ExpData originalOutputData = run.getDataOutputs().getFirst();
        assertEquals(file.getName(), originalOutputData.getName());

        final long dataRowId = originalOutputData.getRowId();
        final String dataLsid = originalOutputData.getLSID();
        final long dataOID = originalOutputData.getObjectId();
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
        assertEquals(dataOID, data2.getObjectId().longValue());
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
        assertEquals(dataOID, data3.getObjectId().longValue());
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
        var assayOutputData = dataOutputs.getFirst();
        assertNotEquals(firstData, assayOutputData);
        assertEquals(firstData.getDataFileUrl(), assayOutputData.getDataFileUrl());

        var dataList = ExperimentService.get().getAllExpDataByURL(firstData.getDataFileUrl(), null);
        assertEquals(assayOutputData, dataList.get(0));
        assertEquals(firstData, dataList.get(1));
        assertEquals(2, dataList.size());

        log.info("delete the run and verify the duplicate exp.data was also deleted");
        assayRun.delete(user);
        dataList = ExperimentService.get().getAllExpDataByURL(firstData.getDataFileUrl(), null);
        assertEquals(firstData, dataList.getFirst());
        assertEquals(1, dataList.size());
    }

    private Map<String, Object> getRealResult(DbSchema schema, String assayResultRealTable, long resultRowId)
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
        long runRowId = run.getRowId();

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
        long resultRowId = MapUtils.getLong(originalQueryResult,"rowid");

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
        assertEquals("modifiedRunResults should have the same Created", modifiedRunResults.get("Created"), runOriginalCreated);
        assertNotEquals("modifiedRunResults should have a different Modified", modifiedRunResults.get("Modified"), runOriginalModified);

        // verify results created/modified matches run's created in query table
        Map<String, Object> queryResultAfterRunModify = new TableSelector(resultsTable, selectColumns, new SimpleFilter(runFieldKey, runRowId), null).getMap();
        assertEquals("queryResultAfterRunModify should have the same Created", queryResultAfterRunModify.get("Created"), runOriginalCreated);
        assertEquals("queryResultAfterRunModify should have the same Modified", queryResultAfterRunModify.get("Modified"), runOriginalCreated);
        assertNotEquals("queryResultAfterRunModify should have a different Modified", queryResultAfterRunModify.get("Modified"), modifiedRunResults.get("Modified"));

        // verify created/modified in provisioned result table is still not populated after run edit
        dbResult = getRealResult(resultsTable.getSchema(), realResultsTable.getName(), resultRowId);
        assertTrue("Created and Modified in the provisioned result table weren't as expected", dbResult.get("Created") == null && dbResult.get("Modified") == null);

        // now edit the result
        QueryUpdateService resultsQUS = resultsTable.getUpdateService();
        updated = new CaseInsensitiveHashMap<>();
        updated.put("ResultProp", 200);
        updated.put("RowId", resultRowId);
        errors = new BatchValidationException();
        Thread.sleep(5);
        resultsQUS.updateRows(user, c, Collections.singletonList(updated), null, errors, null, null);

        // verify result created matches run's created in query table, but result modified now differs from run's created
        Map<String, Object> modifiedResults = new TableSelector(resultsTable, selectColumns, new SimpleFilter(runFieldKey, runRowId), null).getMap();
        assertEquals("modifiedResults Created didn't match runOriginalCreated", modifiedResults.get("Created"), runOriginalCreated);
        assertNotEquals("modifiedResults Created shouldn't match modifiedResult Modified", modifiedResults.get("Created"), modifiedResults.get("Modified"));
        assertNotEquals("modifiedResults Modified shouldn't match runOriginalCreated", modifiedResults.get("Modified"), runOriginalCreated);
        assertNotEquals("modifiedResults Modified shouldn't match runOriginalModified", modifiedResults.get("Modified"), runOriginalModified);
        assertNotEquals("modifiedResults Modified shouldn't match modifiedRunResults Modified", modifiedResults.get("Modified"), modifiedRunResults.get("Modified"));

        // verify modified in provisioned result table no longer null after result edit
        dbResult = getRealResult(resultsTable.getSchema(), realResultsTable.getName(), resultRowId);
        assertTrue("dbResult shouldn't have Created or CreatedBy", dbResult.get("Created") == null && dbResult.get("CreatedBy") == null);
        assertFalse("dbResult didn't have a Modified or ModifiedBy", dbResult.get("Modified") == null || dbResult.get("ModifiedBy") == null);
        assertEquals("dbResults Modified didn't match", dbResult.get("Modified"), modifiedResults.get("Modified"));
        assertEquals("dbResults ModifiedBy didn't match", dbResult.get("ModifiedBy"), modifiedResults.get("ModifiedBy"));
    }

    @Test
    public void testSampleLookupUpdate() throws Exception
    {
        // Regression coverage for Issue 45594 and Issue 51316.
        // Verify run/result properties backed by lineage update when query data is updated.

        // Arrange
        var assayPair = createAssay(context, true, true);
        var provider = assayPair.first;
        var assayProtocol = assayPair.second;

        var samples = createSamples(10);
        var runSample1 = samples.get(0);
        var runSample2 = samples.get(1);
        var resultSample1 = samples.get(2);
        var resultSample2 = samples.get(3);
        var resultSample3 = samples.get(4);
        var resultSample4 = samples.get(5);
        var sampleLookupSample = samples.get(6);

        // create a file in the pipeline root to import
        var fileContents = String.format(
            "ResultExpMaterialsLookup\tSampleTypeLookup\n%s\t%s\n%s\n%s\n",
            resultSample1.getName(), sampleLookupSample.getName(),
            resultSample3.getName(),
            resultSample3.getName()
        );

        // create a run
        var run = assayImportFile(c, user, provider, assayProtocol, createAssayDataFile(fileContents), false, Map.of("RunExpMaterialsLookup", runSample1.getName()));

        // Verify pre-conditions
        assertEquals(4, run.getMaterialInputs().size());
        assertEquals("RunExpMaterialsLookup", run.getMaterialInputs().get(runSample1));
        assertEquals("ResultExpMaterialsLookup", run.getMaterialInputs().get(resultSample1));
        assertEquals("ResultExpMaterialsLookup", run.getMaterialInputs().get(resultSample3));
        assertEquals("SampleTypeLookup", run.getMaterialInputs().get(sampleLookupSample));

        AssayProtocolSchema schema = provider.createProtocolSchema(user, c, assayProtocol, null);
        TableInfo runsTable = schema.getTable("Runs");
        TableInfo resultsTable = schema.getTable("Data");

        // Act
        // Replace "RunExpMaterialsLookup" lookup runSample1 -> runSample2
        var errors = new BatchValidationException();
        Map<String, Object> updatedRunRow = CaseInsensitiveHashMap.of("RowId", run.getRowId(), "RunExpMaterialsLookup", runSample2.getRowId());
        runsTable.getUpdateService().updateRows(user, c, List.of(updatedRunRow), null, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        // Assert
        var updatedRun = ExperimentService.get().getExpRun(run.getRowId());
        assertNotNull(updatedRun);

        assertEquals(4, updatedRun.getMaterialInputs().size());
        assertEquals("RunExpMaterialsLookup", updatedRun.getMaterialInputs().get(runSample2)); // Run row
        assertEquals("ResultExpMaterialsLookup", updatedRun.getMaterialInputs().get(resultSample1)); // Result row 1
        assertEquals("ResultExpMaterialsLookup", updatedRun.getMaterialInputs().get(resultSample3)); // Result row 3
        assertEquals("SampleTypeLookup", updatedRun.getMaterialInputs().get(sampleLookupSample)); // Result row 1

        // Act
        // Replace some lookup values on results
        var resultRows = new TableSelector(resultsTable).getMapArray();
        var updatedResultRow1 = CaseInsensitiveHashMap.of("RowId", resultRows[0].get("RowId"), "ResultExpMaterialsLookup", resultSample2.getRowId());
        var updatedResultRow2 = CaseInsensitiveHashMap.of("RowId", resultRows[1].get("RowId"), "resultProp", 42);
        var updatedResultRow3 = CaseInsensitiveHashMap.of("RowId", resultRows[2].get("RowId"), "ResultExpMaterialsLookup", resultSample4.getRowId());
        resultsTable.getUpdateService().updateRows(user, c, List.of(updatedResultRow1, updatedResultRow2, updatedResultRow3), null, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        // Assert
        updatedRun = ExperimentService.get().getExpRun(run.getRowId());
        assertNotNull(updatedRun);

        assertEquals(5, updatedRun.getMaterialInputs().size());
        assertEquals("RunExpMaterialsLookup", updatedRun.getMaterialInputs().get(runSample2)); // Run row
        assertEquals("ResultExpMaterialsLookup", updatedRun.getMaterialInputs().get(resultSample2)); // Result row 1
        assertEquals("ResultExpMaterialsLookup", updatedRun.getMaterialInputs().get(resultSample3)); // Result row 2
        assertEquals("ResultExpMaterialsLookup", updatedRun.getMaterialInputs().get(resultSample4)); // Result row 3
        assertEquals("SampleTypeLookup", updatedRun.getMaterialInputs().get(sampleLookupSample)); // Result row 1

        // Act
        // Clear run lookup value
        updatedRunRow = CaseInsensitiveHashMap.of("RowId", run.getRowId(), "RunExpMaterialsLookup", null);
        runsTable.getUpdateService().updateRows(user, c, List.of((Map) updatedRunRow), null, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        // Assert
        updatedRun = ExperimentService.get().getExpRun(run.getRowId());
        assertNotNull(updatedRun);

        assertEquals(4, updatedRun.getMaterialInputs().size());
        assertEquals("ResultExpMaterialsLookup", updatedRun.getMaterialInputs().get(resultSample2)); // Result row 1
        assertEquals("ResultExpMaterialsLookup", updatedRun.getMaterialInputs().get(resultSample3)); // Result row 2
        assertEquals("ResultExpMaterialsLookup", updatedRun.getMaterialInputs().get(resultSample4)); // Result row 3
        assertEquals("SampleTypeLookup", updatedRun.getMaterialInputs().get(sampleLookupSample)); // Result row 1

        // Act
        // Clear result lookup values
        resultRows = new TableSelector(resultsTable).getMapArray();
        updatedResultRow1 = CaseInsensitiveHashMap.of("RowId", resultRows[0].get("RowId"), "ResultExpMaterialsLookup", null, "SampleTypeLookup", null);
        updatedResultRow2 = CaseInsensitiveHashMap.of("RowId", resultRows[1].get("RowId"), "ResultExpMaterialsLookup", null);
        updatedResultRow3 = CaseInsensitiveHashMap.of("RowId", resultRows[2].get("RowId"), "ResultExpMaterialsLookup", null);
        resultsTable.getUpdateService().updateRows(user, c, List.of(updatedResultRow1, updatedResultRow2, updatedResultRow3), null, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        // Assert
        updatedRun = ExperimentService.get().getExpRun(run.getRowId());
        assertNotNull(updatedRun);

        assertEquals(0, updatedRun.getMaterialInputs().size());
    }

    @Test // GH Issue 1781
    public void testSampleLookupRowIdFallbackHonorsSampleType() throws Exception
    {
        createSampleType("XSampleType", "X-${genId}");
        createSampleType("YSampleType", "Y-${genId}");
        var xSample = createSamples("XSampleType", 1).getFirst();
        var ySample = createSamples("YSampleType", 1).getFirst();

        var xLookup = new GWTPropertyDescriptor("XLookup", "string");
        xLookup.setLookupSchema(SamplesSchema.SCHEMA_NAME);
        xLookup.setLookupQuery("XSampleType");

        var yLookup = new GWTPropertyDescriptor("YLookup", "int");
        yLookup.setLookupSchema(ExpSchema.SCHEMA_EXP_MATERIALS.toString());
        yLookup.setLookupQuery("YSampleType");

        var assayPair = createAssay(context, false, false, List.of(xLookup, yLookup));
        var provider = assayPair.first;
        var assayProtocol = assayPair.second;

        // Mixing a name into YLookup keeps the column as text, so the X row ID reaches findExpMaterial's row ID fallback
        String expectedError = importErrorMessage(provider, assayProtocol, String.format(
            "YLookup\n%s\n%d\n",
            ySample.getName(),
            xSample.getRowId()
        ));
        assertTrue(expectedError, expectedError.contains("not found in the current context"));

        // XLookup resolving xSample first must not let YLookup's fallback return it from XLookup's material cache
        String actualError = importErrorMessage(provider, assayProtocol, String.format(
            "XLookup\tYLookup\n%s\t%s\n\t%d\n",
            xSample.getName(), ySample.getName(),
            xSample.getRowId()
        ));
        assertEquals(expectedError, actualError);
    }

    @Test
    public void testSampleLookupByIdHonorsContainerScope() throws Exception
    {
        // Without a lookup container filter, by-ID lookups once resolved samples from any container
        assertNull(QueryService.get().getContainerFilterTypeForLookups(c));

        createSampleType("LocalScope", "Local-${genId}");
        var local = createSamples("LocalScope", 1).getFirst();

        // The test container is /Shared/_junit, so a child folder is outside CurrentPlusProjectAndShared
        Container child = ContainerManager.createContainer(c, "OutOfScope", user);
        createSampleType(child, "ChildScope", "Child-${genId}");
        var outOfScope = insertSamples(child, "ChildScope", List.of(CaseInsensitiveHashMap.of())).getFirst();

        Container shared = ContainerManager.getSharedContainer();
        ExpSampleType sharedType = createSampleType(shared, "SharedScope" + GUID.makeHash(), "Shared-${genId}");
        List<ExpRun> runs = new ArrayList<>();
        try
        {
            var sharedSample = insertSamples(shared, sharedType.getName(), List.of(CaseInsensitiveHashMap.of())).getFirst();

            var assayPair = createAssay(context, false, false);
            String fileContents = String.format("ResultExpMaterialsLookup\n%d\n%d\n%d\n", local.getRowId(), sharedSample.getRowId(), outOfScope.getRowId());

            for (boolean allowLookupByAlternateKey : List.of(true, false))
            {
                ExpRun run = assayImportFile(c, user, assayPair.first, assayPair.second, createAssayDataFile(fileContents), false, null, allowLookupByAlternateKey);
                runs.add(run);

                Set<Long> inputIds = new HashSet<>();
                run.getMaterialInputs().keySet().forEach(m -> inputIds.add(m.getRowId()));
                assertEquals("allowLookupByAlternateKey " + allowLookupByAlternateKey, Set.of(local.getRowId(), sharedSample.getRowId()), inputIds);
            }
        }
        finally
        {
            runs.forEach(run -> run.delete(user));
            sharedType.delete(user);
        }
    }

    private String importErrorMessage(AssayProvider provider, ExpProtocol assayProtocol, String fileContents) throws Exception
    {
        try
        {
            assayImportFile(c, user, provider, assayProtocol, createAssayDataFile(fileContents), false, null);
        }
        catch (ExperimentException | ValidationException e)
        {
            return e.getMessage();
        }
        fail("Expected the import to fail");
        return null;
    }

    @Test // GH Issue 1746
    public void testSampleLookupQueriesDoNotScaleWithDistinctSamples() throws Exception
    {
        var assayPair = createAssay(context, false, true);
        var provider = assayPair.first;
        var assayProtocol = assayPair.second;

        final int rowCount = 20;
        final int fewerSamples = 10;
        Iterator<ExpMaterial> unusedSamples = createSamples(1 + 4 * (fewerSamples + rowCount)).iterator();

        // Pay one-time costs, such as loading the assay design and lookup tables, outside the counted imports
        var warmUpSample = unusedSamples.next();
        assayImportFile(c, user, provider, assayProtocol, createAssayDataFile(String.format(
            "SampleTypeLookup\tExpMaterialSampleTypeLookup\n%s\t%d\n",
            warmUpSample.getName(), warmUpSample.getRowId()
        )), false, null);

        // The provenance callback looks up each distinct input sample on its own
        int allowedQueriesPerSample = 1;
        if (ProvenanceService.get().isProvenanceSupported())
            allowedQueriesPerSample = 2;

        List<String> failures = new ArrayList<>();
        try (AutoCloseable ignored = BatchSize.override(BatchSize.Kind.ROWS, 3))
        {
            // Without alternate keys, by-ID values skip findExpMaterial
            for (boolean allowLookupByAlternateKey : List.of(true, false))
            {
                for (String column : List.of("SampleTypeLookup", "ExpMaterialSampleTypeLookup"))
                {
                    // Same row count in both imports, so per-row costs such as result inserts cancel out; each import gets its own samples so neither warms the other
                    List<String> repeatedValues = new ArrayList<>();
                    for (int i = 0; i < fewerSamples; i++)
                    {
                        String value = sampleLookupValue(column, unusedSamples.next());
                        repeatedValues.add(value);
                        repeatedValues.add(value);
                    }

                    List<String> distinctValues = new ArrayList<>();
                    for (int i = 0; i < rowCount; i++)
                        distinctValues.add(sampleLookupValue(column, unusedSamples.next()));

                    int repeatedQueries = countImportQueries(provider, assayProtocol, column, repeatedValues, allowLookupByAlternateKey);
                    int distinctQueries = countImportQueries(provider, assayProtocol, column, distinctValues, allowLookupByAlternateKey);
                    String counts = String.format("%s (allowLookupByAlternateKey %s): %d queries for %d distinct samples, %d queries for %d distinct samples",
                        column, allowLookupByAlternateKey, repeatedQueries, fewerSamples, distinctQueries, rowCount);
                    log.info(counts);

                    if (distinctQueries - repeatedQueries >= allowedQueriesPerSample * (rowCount - fewerSamples))
                        failures.add(counts);
                }
            }
        }

        assertTrue(String.join("; ", failures), failures.isEmpty());
    }

    @Test // GH Issue 1746
    public void testSampleLookupsResolveAcrossLookAheadWindows() throws Exception
    {
        var assayPair = createAssay(context, false, true);
        var provider = assayPair.first;
        var assayProtocol = assayPair.second;

        List<ExpMaterial> s = createSamples(6);
        // Issue 47509: a name that is another sample's row ID resolves by name, in both columns
        ExpMaterial integerNamed = createNamedSample(SAMPLE_TYPE_NAME, String.valueOf(s.get(5).getRowId()));

        // Windows of 3 rows: [1-3], [4-6], [7]; s0, s1 and s3 repeat in later windows
        String fileContents = String.format(
            "SampleTypeLookup\tExpMaterialSampleTypeLookup\n%s\t%d\n%s\t%d\n%s\t\n%s\t%d\n%s\t%d\n%s\t%d\n%s\t%d\n",
            s.get(0).getName(), s.get(1).getRowId(),
            s.get(1).getName(), s.get(2).getRowId(),
            integerNamed.getName(),
            "NoSuchSample", s.get(3).getRowId(),
            s.get(0).getName(), s.get(0).getRowId(),
            s.get(2).getName(), s.get(5).getRowId(),
            s.get(3).getName(), s.get(1).getRowId()
        );

        ExpRun run;
        try (AutoCloseable ignored = BatchSize.override(BatchSize.Kind.ROWS, 3))
        {
            run = assayImportFile(c, user, provider, assayProtocol, createAssayDataFile(fileContents), false, null);
        }

        assertEquals(
            Set.of(s.get(0), s.get(1), s.get(2), s.get(3), integerNamed),
            run.getMaterialInputs().keySet()
        );

        TableInfo resultsTable = provider.createProtocolSchema(user, c, assayProtocol, null).getTable("Data");
        List<Long> storedIds = new TableSelector(resultsTable.getColumn("ExpMaterialSampleTypeLookup"), null, new Sort("RowId")).getArrayList(Long.class);
        assertEquals(
            Arrays.asList(s.get(1).getRowId(), s.get(2).getRowId(), null, s.get(3).getRowId(), s.get(0).getRowId(), integerNamed.getRowId(), s.get(1).getRowId()),
            storedIds
        );
    }

    @Test // GH Issue 1746
    public void testSampleLookupErrorsMatchAcrossLookAheadWindows() throws Exception
    {
        var anySampleLookup = new GWTPropertyDescriptor("AnySampleLookup", "string");
        anySampleLookup.setLookupSchema(ExpSchema.SCHEMA_NAME);
        anySampleLookup.setLookupQuery(ExpSchema.TableType.Materials.name());

        var assayPair = createAssay(context, false, true, List.of(anySampleLookup));
        var provider = assayPair.first;
        var assayProtocol = assayPair.second;

        List<ExpMaterial> s = createSamples(5);
        createSampleType("OtherSampleType", "O-${genId}");
        // Ambiguous in exp.Materials: two sample types hold this name
        String sharedName = createNamedSample("OtherSampleType", s.get(0).getName()).getName();

        List<String> validRows = List.of(
            String.format("%s\t%d\t", s.get(1).getName(), s.get(1).getRowId()),
            String.format("%s\t%d\t", s.get(2).getName(), s.get(2).getRowId()),
            String.format("%s\t%d\t", s.get(3).getName(), s.get(3).getRowId()),
            String.format("%s\t%d\t", s.get(4).getName(), s.get(4).getRowId())
        );
        String header = "SampleTypeLookup\tExpMaterialSampleTypeLookup\tAnySampleLookup\n";
        // A missing row ID in an all-numeric column isn't reported, so use a missing name, which keeps the value a string
        String missingSample = String.format("%s\tNoSuchSample\t", s.get(1).getName());
        String ambiguousName = String.format("%s\t%d\t%s", s.get(1).getName(), s.get(1).getRowId(), sharedName);

        try (AutoCloseable ignored = BatchSize.override(BatchSize.Kind.ROWS, 3))
        {
            // The fifth row is the second row of the second window
            String precedingRows = header + String.join("\n", validRows) + "\n";

            Exception alone = importFailure(provider, assayProtocol, header + missingSample + "\n");
            Exception fifth = importFailure(provider, assayProtocol, precedingRows + missingSample + "\n");
            assertEquals(alone.getMessage(), fifth.getMessage());
            assertEquals(fifth.getMessage(), 1, failedRow(alone));
            assertEquals(fifth.getMessage(), 5, failedRow(fifth));

            // remappableLookup's remap rejects an ambiguous name with a bare ConversionException, so there's no row number
            alone = importFailure(provider, assayProtocol, header + ambiguousName + "\n");
            fifth = importFailure(provider, assayProtocol, precedingRows + ambiguousName + "\n");
            assertEquals(alone.getMessage(), fifth.getMessage());
            assertTrue(fifth.getMessage(), fifth.getMessage().contains("Found 2 values matching: " + sharedName));
        }
    }

    private Exception importFailure(AssayProvider provider, ExpProtocol assayProtocol, String fileContents) throws Exception
    {
        try
        {
            assayImportFile(c, user, provider, assayProtocol, createAssayDataFile(fileContents), false, null);
        }
        catch (ExperimentException | ValidationException | ConversionException e)
        {
            return e;
        }
        fail("Expected the import to fail: " + fileContents);
        return null;
    }

    private static int failedRow(Throwable e)
    {
        for (Throwable t = e; t != null; t = t.getCause())
        {
            if (t instanceof BatchValidationException bve && bve.getLastRowError() != null)
                return bve.getLastRowError().getRowNumber();
            if (t instanceof ValidationException ve && ve.getRowNumber() != -1)
                return ve.getRowNumber();
        }
        return -1;
    }

    private String sampleLookupValue(String column, ExpMaterial sample)
    {
        if ("SampleTypeLookup".equals(column))
            return sample.getName();
        return String.valueOf(sample.getRowId());
    }

    private int countImportQueries(AssayProvider provider, ExpProtocol assayProtocol, String column, List<String> values, boolean allowLookupByAlternateKey) throws Exception
    {
        var file = createAssayDataFile(column + "\n" + String.join("\n", values) + "\n");
        try (QueryCounter counter = new QueryCounter())
        {
            assayImportFile(c, user, provider, assayProtocol, file, false, null, allowLookupByAlternateKey);
            return counter.getCount();
        }
    }

    @Test
    public void testDomainFieldConfiguration() throws Exception
    {
        // Validates assay domain fields are configured as expected by DomainUtil and not overwritten
        // Arrange
        var sampleTypeName = "Does Not Exist";
        var lookupName = "Field100 ABCDEFGHIJKLMNOPQRSTUVWXYZ%()=+-[]_|*`'\":;<>?!@#^AABBCCDDEEFFGGHHIIJJKKLLMMNNOOPPQQRRSSTTU)";
        var assayPair = createAssay(context, true, true);
        var domainService = new AssayDomainServiceImpl(context.getUser(), context.getContainer());
        var gwtProtocol = domainService.getAssayDefinition(assayPair.second.getRowId(), false);

        // Update the assay design to refer to a non-existent sample type
        {
            var resultDomain = getResultDomain(gwtProtocol);
            var resultFields = resultDomain.getFields(true);

            GWTPropertyDescriptor invalidSampleLookup = new GWTPropertyDescriptor("SampleTypeLookup", "string");
            invalidSampleLookup.setLookupSchema(SamplesSchema.SCHEMA_NAME);
            invalidSampleLookup.setLookupQuery(sampleTypeName);
            invalidSampleLookup.setName(lookupName);
            resultFields.add(invalidSampleLookup);
            resultDomain.setFields(resultFields);
            gwtProtocol = domainService.saveChanges(gwtProtocol, true);
        }

        // Act
        var updatedResultsDomain = getResultDomain(gwtProtocol);

        // Assert
        var lookup = updatedResultsDomain.getFieldByName(lookupName);
        assertFalse("Expected lookup to be marked as invalid since sample type does not exist by that name", lookup.getLookupIsValid());
        assertEquals("Expected lookup query to point to non-existent sample type", sampleTypeName, lookup.getLookupQuery());
    }

    private GWTDomain<GWTPropertyDescriptor> getResultDomain(GWTProtocol protocol)
    {
        return protocol.getDomains().stream().filter(d -> "Data".equals(d.getQueryName())).findFirst().orElseThrow();
    }
%>
