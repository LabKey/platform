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
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.PostCommand;
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
import org.labkey.remoteapi.domain.DomainResponse;
import org.labkey.remoteapi.domain.GetDomainCommand;
import org.labkey.remoteapi.domain.ListDomainsCommand;
import org.labkey.remoteapi.domain.ListDomainsResponse;
import org.labkey.remoteapi.domain.PropertyDescriptor;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@Category({Daily.class})
public class ExperimentAPITest extends BaseWebDriverTest
{
    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @BeforeClass
    public static void setupProject()
    {
        ExperimentAPITest init = (ExperimentAPITest) getCurrentTest();

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
        final String sampleSetName = "My Set";

        createSampleSet(sampleSetName);

        Batch batch = new Batch();
        batch.setName("testSaveBatchSampleSetMaterials Batch");

        JSONObject sampleSet = new JSONObject();
        sampleSet.put("name", sampleSetName);

        JSONObject sampleSetMaterial1 = new JSONObject();
        sampleSetMaterial1.put("name", "testSaveBatchSampleSetMaterials-ss-1");
        sampleSetMaterial1.put("sampleSet", sampleSet);

        JSONObject sampleSetMaterial2 = new JSONObject();
        sampleSetMaterial2.put("name", "testSaveBatchSampleSetMaterials-ss-2");
        sampleSetMaterial2.put("sampleSet", sampleSet);

        Run run1 = new Run();
        run1.setName("testSaveBatchMaterials Run 1");
        run1.setMaterialOutputs(Arrays.asList(new Material(sampleSetMaterial1)));

        Run run2 = new Run();
        run2.setName("testSaveBatchMaterials Run 2");
        run2.setMaterialInputs(Arrays.asList(new Material(sampleSetMaterial1)));
        run2.setMaterialOutputs(Arrays.asList(new Material(sampleSetMaterial2)));

        batch.getRuns().add(run1);
        batch.getRuns().add(run2);

        SaveAssayBatchCommand cmd = new SaveAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, batch);
        cmd.setTimeout(10000);
        Connection connection = createDefaultConnection();
        SaveAssayBatchResponse response = cmd.execute(connection, getProjectName());
        int batchId = response.getBatch().getId();

        Batch responseBatch = getBatch(connection, batchId);
        assertEquals("Runs in batch: " + responseBatch.toJSONObject().toJSONString(),
                2, responseBatch.getRuns().size());
        assertEquals("Materials in run: " + responseBatch.toJSONObject().toJSONString(),
                3, responseBatch.getRuns().stream().mapToInt(run -> run.getMaterialInputs().size() + run.getMaterialOutputs().size()).sum());
        assertEquals("Matching experiment materials should have the same id: " + responseBatch.toJSONObject().toJSONString(),
                responseBatch.getRuns().get(0).getMaterialOutputs().get(0).getId(), responseBatch.getRuns().get(1).getMaterialInputs().get(0).getId());
    }

    private void createSampleSet(String sampleSetName)
    {
        log("Create sample type");
        goToModule("Experiment");
        SampleTypeHelper sampleHelper = new SampleTypeHelper(this);
        sampleHelper.createSampleType(new SampleTypeDefinition(sampleSetName)
                        .setFields(List.of(
                                new FieldDefinition("IntCol", FieldDefinition.ColumnType.Integer),
                                new FieldDefinition("StringCol", FieldDefinition.ColumnType.String),
                                new FieldDefinition("DateCol", FieldDefinition.ColumnType.DateAndTime),
                                new FieldDefinition("BoolCol", FieldDefinition.ColumnType.Boolean))),
                TestFileUtils.getSampleData("sampleType.xlsx"));
    }

    @Test @Ignore(/*TODO*/"35654: Can't reference experiment materials by name if they aren't associated with a sampleset")
    public void testSaveBatchMaterials() throws Exception
    {
        Batch batch = new Batch();
        batch.setName("testSaveBatchMaterials Batch");

        JSONObject material1 = new JSONObject();
        material1.put("name", "testSaveBatchMaterials-1");

        JSONObject material2 = new JSONObject();
        material2.put("name", "testSaveBatchMaterials-2");

        Run run1 = new Run();
        run1.setName("testSaveBatchMaterials Run 1");
        run1.setMaterialOutputs(Arrays.asList(new Material(material1)));

        Run run2 = new Run();
        run2.setName("testSaveBatchMaterials Run 2");
        run2.setMaterialInputs(Arrays.asList(new Material(material1)));
        run2.setMaterialOutputs(Arrays.asList(new Material(material2)));

        batch.getRuns().add(run1);
        batch.getRuns().add(run2);

        SaveAssayBatchCommand cmd = new SaveAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, batch);
        cmd.setTimeout(10000);
        Connection connection = createDefaultConnection();
        SaveAssayBatchResponse response = cmd.execute(connection, getProjectName());
        int batchId = response.getBatch().getId();

        Batch responseBatch = getBatch(connection, batchId);
        assertEquals("Runs in batch: " + responseBatch.toJSONObject().toJSONString(),
                2, responseBatch.getRuns().size());
        assertEquals("Materials in run: " + responseBatch.toJSONObject().toJSONString(),
                3, responseBatch.getRuns().stream().mapToInt(run -> run.getMaterialInputs().size() + run.getMaterialOutputs().size()).sum());
        assertEquals("Matching experiment materials should have the same id: " + responseBatch.toJSONObject().toJSONString(),
                responseBatch.getRuns().get(0).getMaterialOutputs().get(0).getId(), responseBatch.getRuns().get(1).getMaterialInputs().get(0).getId());
    }

    @Test
    public void testSaveBatchDatas() throws Exception
    {
        File file1 = TestFileUtils.getSampleData("pipeline/sample1.testIn.tsv");
        File file2 = TestFileUtils.getSampleData("pipeline/sample2.testIn.tsv");
        goToModule("FileContent");
        _fileBrowserHelper.uploadFile(file1);
        _fileBrowserHelper.uploadFile(file2);

        Batch batch = new Batch();
        batch.setName("testSaveBatchDatas Batch");

        JSONObject d1 = new JSONObject();
        d1.put("pipelinePath", file1.getName());

        JSONObject d2 = new JSONObject();
        d2.put("pipelinePath", file2.getName());

        Run run1 = new Run();
        run1.setName("testSaveBatchDatas Run 1");
        run1.setDataOutputs(Arrays.asList(new Data(d1)));

        Run run2 = new Run();
        run2.setName("testSaveBatchDatas Run 2");
        run2.setDataInputs(Arrays.asList(new Data(d1)));
        run2.setDataOutputs(Arrays.asList(new Data(d2)));

        batch.getRuns().add(run1);
        batch.getRuns().add(run2);

        Connection connection = createDefaultConnection();
        SaveAssayBatchCommand cmd = new SaveAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, batch);
        cmd.setTimeout(10000);
        SaveAssayBatchResponse saveResponse = cmd.execute(connection, getProjectName());
        int batchId = saveResponse.getBatch().getId();

        Batch responseBatch = getBatch(connection, batchId);
        assertEquals("Runs in batch: " + responseBatch.toJSONObject().toJSONString(),
                2, responseBatch.getRuns().size());
        assertEquals("Datas in run: " + responseBatch.toJSONObject().toJSONString(),
                3, responseBatch.getRuns().stream().mapToInt(run -> run.getDataInputs().size() + run.getDataOutputs().size()).sum());
        assertEquals("Matching experiment datas should have the same id: " + responseBatch.toJSONObject().toJSONString(),
                responseBatch.getRuns().get(0).getDataOutputs().get(0).getId(), responseBatch.getRuns().get(1).getDataInputs().get(0).getId());
    }

    @Test
    public void testRunDataBadAbsolutePath() throws Exception
    {
        Batch batch = new Batch();
        batch.setName("testRunDataBadAbsolutePath Batch");

        JSONObject d1 = new JSONObject();
        d1.put("absolutePath", new File(TestFileUtils.getDefaultFileRoot(getProjectName()), "../../../../labkey.xml").getAbsolutePath());

        Run run1 = new Run();
        run1.setName("testRunDataBadAbsolutePath Run 1");
        run1.setDataOutputs(Arrays.asList(new Data(d1)));

        batch.getRuns().add(run1);

        SaveAssayBatchCommand cmd = new SaveAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, batch);
        cmd.setTimeout(10000);
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
        PostCommand getBatch = new PostCommand("assay", "getAssayBatch");
        JSONObject json = new JSONObject();
        json.put("protocolName", SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL);
        json.put("batchId", batchId);
        getBatch.setJsonObject(json);
        CommandResponse getResponse = getBatch.execute(connection, getProjectName());
        return new Batch(getResponse.getProperty("batch"));
    }

    private DomainResponse createDomain(String domainKind, String domainName, String description, List<PropertyDescriptor> fields) throws IOException, CommandException
    {
        CreateDomainCommand domainCommand = new CreateDomainCommand(domainKind, domainName);
        domainCommand.getDomainDesign().setDescription(description);
        domainCommand.getDomainDesign().setFields(fields);

        DomainResponse domainResponse = domainCommand.execute(createDefaultConnection(), getProjectName());
        GetDomainCommand getDomainCommand = new GetDomainCommand(domainResponse.getDomain().getDomainId());
        return getDomainCommand.execute(createDefaultConnection(), getProjectName());
    }

    @Test
    public void testSaveBatchWithAdHocProperties() throws IOException, CommandException
    {
        String domainKind = "Vocabulary";
        String domainName = "TestVocabulary";
        String domainDescription = "Test Ad Hoc Properties";
        String prop1Name = "testIntField";
        String prop2Name = "testStringField";
        String prop1range = "int";
        String prop2range = "string";

        //Create VocabularyDomain with adhoc properties
        List<PropertyDescriptor> fields = new ArrayList<>();
        fields.add(new PropertyDescriptor(prop1Name, prop1range));
        fields.add(new PropertyDescriptor(prop2Name, prop2range));

        DomainResponse domainResponse = createDomain(domainKind, domainName, domainDescription,fields);

        //verifying properties got added in domainResponse
        assertEquals("First Adhoc property not found.", domainResponse.getDomain().getFields().get(0).getName(), prop1Name);
        assertEquals("Second Adhoc property not found.", domainResponse.getDomain().getFields().get(1).getName(), prop2Name);

        //Save Batch - Use Vocabulary Domain properties while saving batch
        List<PropertyDescriptor> propertyURIS = domainResponse.getDomain().getFields();
        Run run = new Run();
        run.setName("testAdHocPropertiesRun");
        run.setProperties(Map.of(propertyURIS.get(1).getPropertyURI(), "testAdHocRunProperty"));

        Batch batch = new Batch();
        batch.setProperties(Map.of(propertyURIS.get(0).getPropertyURI(), 123));
        batch.setRuns(List.of(run));

        SaveAssayBatchCommand saveAssayBatchCommand = new SaveAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, batch);
        SaveAssayBatchResponse saveAssayBatchResponse = saveAssayBatchCommand.execute(createDefaultConnection(), getProjectName());

        LoadAssayBatchCommand loadDomainCommand = new LoadAssayBatchCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, saveAssayBatchResponse.getBatch().getId());
        LoadAssayBatchResponse loadAssayBatchResponse = loadDomainCommand.execute(createDefaultConnection(), getProjectName());
        List<String> addedPropertyURIs = new ArrayList<>(loadAssayBatchResponse.getBatch().getProperties().keySet());

        //Verify property in added batch
        assertEquals("Ad hoc property not found." , propertyURIS.get(0).getPropertyURI(), addedPropertyURIs.get(0));
    }

    @Test
    public void testSaveRunApi() throws IOException, CommandException
    {
        String domainKind = "Vocabulary";
        String domainName = "RunVocabulary";
        String domainDescription = "Test Save Runs";
        String propertyName = "testRunField";
        String rangeURI = "string";

        List<PropertyDescriptor> fields = new ArrayList<>();
        fields.add(new PropertyDescriptor(propertyName, rangeURI));

        DomainResponse domainResponse = createDomain(domainKind, domainName, domainDescription, fields);

        assertEquals("Property not added in Domain.", propertyName, domainResponse.getDomain().getFields().get(0).getName());

        String vocabDomainPropURI = domainResponse.getDomain().getFields().get(0).getPropertyURI();
        String vocabDomainPropVal = "Value 1";

        ListDomainsCommand listDomainsCommand = new ListDomainsCommand(true, false, Set.of("UserAuditDomain"), "/Shared");
        ListDomainsResponse listDomainsResponse = listDomainsCommand.execute(createDefaultConnection(), "Shared");

        String userAuditDomainPropURI = listDomainsResponse.getDomains().get(0).getFields().get(0).getPropertyURI();

        Run runA = new Run();
        runA.setName("testRunA");
        runA.setProperties(Map.of(vocabDomainPropURI, vocabDomainPropVal));

        Run runB = new Run();
        runB.setName("testRunB");
        runB.setProperties(Map.of(userAuditDomainPropURI, 2));

        SaveAssayRunsCommand saveAssayRunsCommand = new SaveAssayRunsCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, List.of(runA, runB));
        SaveAssayRunsResponse saveAssayRunsResponse = saveAssayRunsCommand.execute(createDefaultConnection(), getProjectName());

        String addedRunLsid = saveAssayRunsResponse.getRuns().get(0).getLsid();

        assertEquals("Vocabulary domain property not found in new saved run.", vocabDomainPropVal, saveAssayRunsResponse.getRuns().get(0).getProperties().get(vocabDomainPropURI));
        //assert Non vocabulary domain property not added
        assertTrue("Non Vocabulary domain property found in new saved run.",  saveAssayRunsResponse.getRuns().get(1).getProperties().isEmpty());

        GetAssayRunCommand getAssayRunCommand = new GetAssayRunCommand(addedRunLsid);
        GetAssayRunResponse getAssayRunResponse = getAssayRunCommand.execute(createDefaultConnection(), getProjectName());

        assertEquals("Vocabulary domain property not found in new saved run.", getAssayRunResponse.getRun().getProperties().get(vocabDomainPropURI), vocabDomainPropVal);

        String resultLsid = getAssayRunResponse.getRun().getLsid();

        assertEquals("Run not found", addedRunLsid, resultLsid);
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
        DomainResponse domainResponse = createDomain(domainKind, domainName, domainDescription, List.of(new PropertyDescriptor(propertyName,  rangeURI)));

        assertEquals("Property not added in Vocabulary Domain.", propertyName, domainResponse.getDomain().getFields().get(0).getName());

        String vocabDomainPropURI = domainResponse.getDomain().getFields().get(0).getPropertyURI();
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

        List<Map<String, Object>> dataRows = Arrays.asList(
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
        assertTrue("Ad hoc property is not present in Run.", loadAssayBatchResponse.getBatch().getRuns().get(0).getProperties().containsKey(vocabDomainPropURI));
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
        return Arrays.asList("experiment");
    }
}
