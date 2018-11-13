/*
 * Copyright (c) 2018 LabKey Corporation
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
import org.labkey.remoteapi.assay.Material;
import org.labkey.remoteapi.assay.Run;
import org.labkey.remoteapi.assay.SaveAssayBatchCommand;
import org.labkey.remoteapi.assay.SaveAssayBatchResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyC;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@Category({DailyC.class})
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
        Connection connection = createDefaultConnection(false);
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
        log("Create sample set");
        goToModule("Experiment");
        clickButton("Import Sample Set");
        setFormElement(Locator.id("name"), sampleSetName);
        checkRadioButton(Locator.radioButtonByNameAndValue("uploadType", "file"));
        setFormElement(Locator.tagWithName("input", "file"), TestFileUtils.getSampleData("sampleSet.xlsx").getAbsolutePath());
        waitForFormElementToEqual(Locator.id("idCol1"), "0"); // "KeyCol"
        waitForElement(Locator.css("select#parentCol > option").withText("Parent"));
        Locator.id("parentCol").findElement(getDriver()).sendKeys("Parent"); // combo-box helper doesn't work
        clickButton("Submit");
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
        Connection connection = createDefaultConnection(false);
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

        Connection connection = createDefaultConnection(false);
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
            SaveAssayBatchResponse response = cmd.execute(createDefaultConnection(false), getProjectName());
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
