/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.test.tests.experiment;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.assay.Batch;
import org.labkey.remoteapi.assay.Data;
import org.labkey.remoteapi.assay.GetAssayRunCommand;
import org.labkey.remoteapi.assay.GetAssayRunResponse;
import org.labkey.remoteapi.assay.ImportRunCommand;
import org.labkey.remoteapi.assay.ImportRunResponse;
import org.labkey.remoteapi.assay.LoadAssayBatchCommand;
import org.labkey.remoteapi.assay.LoadAssayBatchResponse;
import org.labkey.remoteapi.assay.Material;
import org.labkey.remoteapi.assay.Run;
import org.labkey.remoteapi.assay.SaveAssayBatchCommand;
import org.labkey.remoteapi.assay.SaveAssayBatchResponse;
import org.labkey.remoteapi.assay.SaveAssayRunsCommand;
import org.labkey.remoteapi.assay.SaveAssayRunsResponse;
import org.labkey.remoteapi.domain.CreateDomainCommand;
import org.labkey.remoteapi.domain.DomainDetailsResponse;
import org.labkey.remoteapi.domain.DomainResponse;
import org.labkey.remoteapi.domain.GetDomainDetailsCommand;
import org.labkey.remoteapi.domain.ListDomainsCommand;
import org.labkey.remoteapi.domain.ListDomainsResponse;
import org.labkey.remoteapi.domain.PropertyDescriptor;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.ReactAssayDesignerPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.params.experiment.SampleTypeDefinition;
import org.labkey.test.util.APIAssayHelper;
import org.labkey.test.util.Maps;
import org.labkey.test.util.SampleTypeHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Category({Daily.class})
public class ExperimentAPITest extends BaseWebDriverTest
{
    @BeforeClass
    public static void setupProject()
    {
        ExperimentAPITest init = getCurrentTest();
        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), "Collaboration");
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void testSaveBatchSampleSetMaterials() throws Exception
    {
        final String sampleTypeName = "My Set";

        log("Create sample type");
        new SampleTypeDefinition(sampleTypeName).setFields(
            List.of(
                new FieldDefinition("IntCol", FieldDefinition.ColumnType.Integer),
                new FieldDefinition("StringCol", FieldDefinition.ColumnType.String),
                new FieldDefinition("DateCol", FieldDefinition.ColumnType.DateAndTime),
                new FieldDefinition("BoolCol", FieldDefinition.ColumnType.Boolean)
            )
        ).create(createDefaultConnection(), getProjectName());

        goToModule("Experiment");
        new SampleTypeHelper(this)
                .goToSampleType(sampleTypeName)
                .bulkImport(TestFileUtils.getSampleData("sampleType.xlsx"));

        Batch batch = new Batch();
        batch.setName("testSaveBatchSampleSetMaterials Batch");

        JSONObject sampleType = new JSONObject();
        sampleType.put("name", sampleTypeName);

        JSONObject material1 = new JSONObject();
        material1.put("name", "testSaveBatchSampleSetMaterials-ss-1");
        material1.put("sampleSet", sampleType);

        JSONObject material2 = new JSONObject();
        material2.put("name", "testSaveBatchSampleSetMaterials-ss-2");
        material2.put("sampleSet", sampleType);

        Run run1 = new Run();
        run1.setName("testSaveBatchMaterials Run 1");
        run1.setMaterialOutputs(List.of(new Material(material1)));

        Run run2 = new Run();
        run2.setName("testSaveBatchMaterials Run 2");
        run2.setMaterialInputs(List.of(new Material(material1)));
        run2.setMaterialOutputs(List.of(new Material(material2)));

        batch.getRuns().add(run1);
        batch.getRuns().add(run2);

        SaveAssayBatchCommand cmd = new SaveAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, batch);
        cmd.setTimeout(10_000);
        Connection connection = createDefaultConnection();
        SaveAssayBatchResponse response = cmd.execute(connection, getProjectName());
        int batchId = response.getBatch().getId();

        Batch responseBatch = getBatch(connection, batchId);
        assertEquals("Runs in batch: " + responseBatch.toJSONObject(),
                2, responseBatch.getRuns().size());
        assertEquals("Materials in run: " + responseBatch.toJSONObject(),
                3, responseBatch.getRuns().stream().mapToInt(run -> run.getMaterialInputs().size() + run.getMaterialOutputs().size()).sum());
        assertEquals("Matching experiment materials should have the same id: " + responseBatch.toJSONObject(),
                responseBatch.getRuns().getFirst().getMaterialOutputs().getFirst().getId(), responseBatch.getRuns().get(1).getMaterialInputs().getFirst().getId());
    }

    @Test
    public void testSaveBatchDatas() throws Exception
    {
        File file1 = TestFileUtils.getSampleData("pipeline/sample1.testIn.tsv");
        File file2 = TestFileUtils.getSampleData("pipeline/sample2.testIn.tsv");
        goToModule("FileContent");
        _fileBrowserHelper.uploadFile(file1);
        _fileBrowserHelper.uploadFile(file2);

        JSONObject d1 = new JSONObject();
        d1.put("pipelinePath", file1.getName());

        JSONObject d2 = new JSONObject();
        d2.put("pipelinePath", file2.getName());

        Run run1 = new Run();
        run1.setName("testSaveBatchDatas Run 1");
        run1.setDataOutputs(List.of(new Data(d1)));

        Run run2 = new Run();
        run2.setName("testSaveBatchDatas Run 2");
        run2.setDataInputs(List.of(new Data(d1)));
        run2.setDataOutputs(List.of(new Data(d2)));

        Batch batch = new Batch();
        batch.setName("testSaveBatchDatas Batch");
        batch.getRuns().add(run1);
        batch.getRuns().add(run2);

        Connection connection = createDefaultConnection();
        SaveAssayBatchCommand cmd = new SaveAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, batch);
        cmd.setTimeout(10_000);
        SaveAssayBatchResponse saveResponse = cmd.execute(connection, getProjectName());
        int batchId = saveResponse.getBatch().getId();

        Batch responseBatch = getBatch(connection, batchId);
        assertEquals("Runs in batch: " + responseBatch.toJSONObject(),
                2, responseBatch.getRuns().size());
        assertEquals("Data in run: " + responseBatch.toJSONObject(),
                3, responseBatch.getRuns().stream().mapToInt(run -> run.getDataInputs().size() + run.getDataOutputs().size()).sum());
        assertEquals("Matching experiment data should have the same id: " + responseBatch.toJSONObject(),
                responseBatch.getRuns().getFirst().getDataOutputs().getFirst().getId(), responseBatch.getRuns().get(1).getDataInputs().getFirst().getId());
    }

    @Test
    public void testRunDataBadAbsolutePath() throws Exception
    {
        JSONObject d1 = new JSONObject();
        d1.put("absolutePath", new File(TestFileUtils.getDefaultFileRoot(getProjectName()), "../../../../labkey.xml").getAbsolutePath());

        Run run1 = new Run();
        run1.setName("testRunDataBadAbsolutePath Run 1");
        run1.setDataOutputs(List.of(new Data(d1)));

        Batch batch = new Batch();
        batch.setName("testRunDataBadAbsolutePath Batch");
        batch.getRuns().add(run1);

        SaveAssayBatchCommand cmd = new SaveAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, batch);
        cmd.setTimeout(10_000);
        try
        {
            SaveAssayBatchResponse response = cmd.execute(createDefaultConnection(), getProjectName());
            fail("Referencing file outside of pipeline root should not be permitted. Response: " + response.getText());
        }
        catch (CommandException expected)
        {
            if (!expected.getMessage().contains("not under the pipeline root for this folder"))
                throw new RuntimeException("saving batch data with bad absolute path did not produce the expected exception.", expected);
        }
    }

    @NotNull
    private Batch getBatch(Connection connection, int batchId) throws IOException, CommandException
    {
        LoadAssayBatchCommand getBatch = new LoadAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, batchId);
        LoadAssayBatchResponse getResponse = getBatch.execute(connection, getProjectName());
        return getResponse.getBatch();
    }

    private DomainDetailsResponse createDomain(String domainKind, String domainName, String description, List<PropertyDescriptor> fields) throws IOException, CommandException
    {
        CreateDomainCommand domainCommand = new CreateDomainCommand(domainKind, domainName);
        domainCommand.getDomainDesign().setDescription(description);
        domainCommand.getDomainDesign().setFields(fields);

        DomainResponse domainResponse = domainCommand.execute(createDefaultConnection(), getProjectName());
        GetDomainDetailsCommand getDomainCommand = new GetDomainDetailsCommand(domainResponse.getDomain().getDomainId());
        return getDomainCommand.execute(createDefaultConnection(), getProjectName());
    }

    @Test
    public void testSaveBatchWithAdHocProperties() throws IOException, CommandException
    {
        String prop1Name = "testIntField";
        String prop2Name = "testStringField";

        // Create VocabularyDomain with adhoc properties
        List<PropertyDescriptor> fields = List.of(
            new PropertyDescriptor(prop1Name, "int"),
            new PropertyDescriptor(prop2Name, "string")
        );

        DomainDetailsResponse domainResponse = createDomain("Vocabulary", "TestVocabulary", "Test Ad Hoc Properties", fields);

        // Verifying properties got added in domainResponse
        assertEquals("First Adhoc property not found.", prop1Name, domainResponse.getDomain().getFields().getFirst().getName());
        assertEquals("Second Adhoc property not found.", prop2Name, domainResponse.getDomain().getFields().get(1).getName());

        // Save Batch - Use Vocabulary Domain properties while saving batch
        List<PropertyDescriptor> propertyURIS = domainResponse.getDomain().getFields();
        Run run = new Run();
        run.setName("testAdHocPropertiesRun");
        run.setProperties(Map.of(propertyURIS.get(1).getPropertyURI(), "testAdHocRunProperty"));

        Batch batch = new Batch();
        batch.setProperties(Map.of(propertyURIS.getFirst().getPropertyURI(), 123));
        batch.setRuns(List.of(run));

        SaveAssayBatchCommand saveAssayBatchCommand = new SaveAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, batch);
        SaveAssayBatchResponse saveAssayBatchResponse = saveAssayBatchCommand.execute(createDefaultConnection(), getProjectName());

        LoadAssayBatchCommand loadDomainCommand = new LoadAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, saveAssayBatchResponse.getBatch().getId());
        LoadAssayBatchResponse loadAssayBatchResponse = loadDomainCommand.execute(createDefaultConnection(), getProjectName());
        List<String> addedPropertyURIs = new ArrayList<>(loadAssayBatchResponse.getBatch().getProperties().keySet());

        // Verify property in the added batch
        assertEquals("Ad hoc property not found." , propertyURIS.getFirst().getPropertyURI(), addedPropertyURIs.getFirst());
    }

    @Test
    public void testSaveRunApi() throws IOException, CommandException
    {
        String domainKind = "Vocabulary";
        String domainName = "RunVocabulary";
        String domainDescription = "Test Save Runs";
        String propertyName = "testRunField";
        String rangeURI = "string";

        List<PropertyDescriptor> fields = List.of(new PropertyDescriptor(propertyName, rangeURI));
        DomainDetailsResponse domainResponse = createDomain(domainKind, domainName, domainDescription, fields);

        assertEquals("Property not added in Domain.", propertyName, domainResponse.getDomain().getFields().getFirst().getName());

        String vocabDomainPropURI = domainResponse.getDomain().getFields().getFirst().getPropertyURI();
        String vocabDomainPropVal = "Value 1";

        ListDomainsCommand listDomainsCommand = new ListDomainsCommand(true, false, Set.of("UserAuditDomain"), "/Shared");
        ListDomainsResponse listDomainsResponse = listDomainsCommand.execute(createDefaultConnection(), "Shared");

        String userAuditDomainPropURI = listDomainsResponse.getDomains().getFirst().getFields().getFirst().getPropertyURI();

        Run runA = new Run();
        runA.setName("testRunA");
        runA.setProperties(Map.of(vocabDomainPropURI, vocabDomainPropVal));

        Run runB = new Run();
        runB.setName("testRunB");
        runB.setProperties(Map.of(userAuditDomainPropURI, 2));

        SaveAssayRunsCommand saveAssayRunsCommand = new SaveAssayRunsCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, List.of(runA, runB));
        SaveAssayRunsResponse saveAssayRunsResponse = saveAssayRunsCommand.execute(createDefaultConnection(), getProjectName());

        String addedRunLsid = saveAssayRunsResponse.getRuns().getFirst().getLsid();

        assertEquals("Vocabulary domain property not found in new saved run.", vocabDomainPropVal, saveAssayRunsResponse.getRuns().getFirst().getProperties().get(vocabDomainPropURI));
        //assert Non vocabulary domain property not added
        assertTrue("Non Vocabulary domain property found in new saved run.",  saveAssayRunsResponse.getRuns().get(1).getProperties().isEmpty());

        GetAssayRunCommand getAssayRunCommand = new GetAssayRunCommand(addedRunLsid);
        GetAssayRunResponse getAssayRunResponse = getAssayRunCommand.execute(createDefaultConnection(), getProjectName());

        assertEquals("Vocabulary domain property not found in new saved run.", vocabDomainPropVal, getAssayRunResponse.getRun().getProperties().get(vocabDomainPropURI));

        String resultLsid = getAssayRunResponse.getRun().getLsid();

        assertEquals("Run not found", addedRunLsid, resultLsid);
    }

    @Test // GitHub Issue #1026
    public void testDataRowsIncludeRowId() throws IOException, CommandException
    {
        String assayName = "RowIdAssay";

        goToManageAssays();
        APIAssayHelper assayHelper = new APIAssayHelper(this);
        ReactAssayDesignerPage assayDesignerPage = assayHelper.createAssayDesign("General", assayName);
        assayDesignerPage.clickFinish();

        final int assayId = assayHelper.getIdFromAssayName(assayName, getProjectName(), false);

        List<Map<String, Object>> dataRows = List.of(
            Maps.of("ptid", "p01", "date", "2017-05-10"),
            Maps.of("ptid", "p02", "date", "2017-05-11")
        );

        Run run = new Run();
        run.setName("RowIdRun");
        run.setResultData(dataRows);

        Batch batch = new Batch();
        batch.setName("RowIdBatch");
        batch.getRuns().add(run);

        Connection connection = createDefaultConnection();
        SaveAssayBatchResponse saveResponse = new SaveAssayBatchCommand(assayId, batch).execute(connection, getProjectName());
        assertDataRowsHaveRowId("SaveAssayBatch response", saveResponse.getParsedData(), dataRows.size());

        int batchId = saveResponse.getBatch().getId();
        LoadAssayBatchCommand loadCmd = new LoadAssayBatchCommand(null, batchId)
        {
            @Override
            public JSONObject getJsonObject()
            {
                JSONObject json = super.getJsonObject();
                json.put("assayId", assayId);
                return json;
            }
        };
        LoadAssayBatchResponse loadResponse = loadCmd.execute(connection, getProjectName());
        assertDataRowsHaveRowId("LoadAssayBatch response", loadResponse.getParsedData(), dataRows.size());
    }

    @SuppressWarnings("unchecked")
    private void assertDataRowsHaveRowId(String context, Map<String, Object> parsedData, int expectedRowCount)
    {
        Map<String, Object> batchData = (Map<String, Object>) parsedData.get("batch");
        List<Map<String, Object>> runs = (List<Map<String, Object>>) batchData.get("runs");
        assertEquals(context + ": expected one run", 1, runs.size());
        List<Map<String, Object>> responseDataRows = (List<Map<String, Object>>) runs.getFirst().get("dataRows");
        assertEquals(context + ": unexpected data row count", expectedRowCount, responseDataRows.size());
        for (Map<String, Object> row : responseDataRows)
        {
            Object rowId = row.get("RowId");
            assertTrue(context + ": RowId missing or not a number in " + row, rowId instanceof Number);
            assertTrue(context + ": RowId should be non-zero in " + row, ((Number) rowId).intValue() > 0);
        }
    }

    @Test
    public void testImportRunWithAdhocProperties() throws IOException, CommandException
    {
        String domainKind = "Vocabulary";
        String domainName = "ImportRunVocabulary";
        String domainDescription = "Test Import Runs";
        String propertyName = "TestImportRunField";
        String rangeURI = "int";
        String assayName = "ImportRunAssay";

        // 1. Create Vocabulary Domain with one adhoc property with CreateDomainApi
        DomainDetailsResponse domainResponse = createDomain(domainKind, domainName, domainDescription, List.of(new PropertyDescriptor(propertyName,  rangeURI)));

        assertEquals("Property not added in Vocabulary Domain.", propertyName, domainResponse.getDomain().getFields().getFirst().getName());

        String vocabDomainPropURI = domainResponse.getDomain().getFields().getFirst().getPropertyURI();
        int vocabDomainPropVal = 2;

        // 2. Use this adhoc property as a run property and batch a property in ImportRun api
        goToManageAssays();
        APIAssayHelper assayHelper = new APIAssayHelper(this);
        ReactAssayDesignerPage assayDesignerPage = assayHelper.createAssayDesign("General", assayName);
        assayDesignerPage.goToRunFields()
            .addField("RunIntField")
            .setLabel("Run Int Field")
            .setType(FieldDefinition.ColumnType.Integer);
        assayDesignerPage.clickFinish();

        int assayId = assayHelper.getIdFromAssayName(assayName, getProjectName(), false);

        List<Map<String, Object>> dataRows = List.of(
                Maps.of("ptid", "p01", "date", "2017-05-10")
        );

        ImportRunCommand importRunCommand = new ImportRunCommand(assayId, dataRows);
        importRunCommand.setName("TestImportRun");
        importRunCommand.setBatchProperties(Map.of(vocabDomainPropURI, vocabDomainPropVal));
        importRunCommand.setProperties(Map.of("RunIntField", 10, vocabDomainPropURI, vocabDomainPropVal));
        ImportRunResponse importRunResponse = importRunCommand.execute(createDefaultConnection(), getProjectName());

        assertEquals("Import Run is not successful", assayId, importRunResponse.getAssayId());

        // 3. Verify these properties were added by LoadAssayBatch or LoadAssayRun
        LoadAssayBatchCommand loadAssayBatchCommand = new LoadAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, importRunResponse.getBatchId());
        LoadAssayBatchResponse loadAssayBatchResponse = loadAssayBatchCommand.execute(createDefaultConnection(), getProjectName());
        assertTrue("Ad hoc property is not present in Batch.", loadAssayBatchResponse.getBatch().getProperties().containsKey(vocabDomainPropURI));
        assertTrue("Ad hoc property is not present in Run.", loadAssayBatchResponse.getBatch().getRuns().getFirst().getProperties().containsKey(vocabDomainPropURI));
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "ExperimentAPITest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return List.of("experiment");
    }
}
