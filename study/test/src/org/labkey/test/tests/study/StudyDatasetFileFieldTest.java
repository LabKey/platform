package org.labkey.test.tests.study;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Daily;
import org.labkey.test.components.domain.DomainFormPanel;
import org.labkey.test.components.ext4.Checkbox;
import org.labkey.test.pages.DatasetInsertPage;
import org.labkey.test.pages.study.DatasetDesignerPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.DataRegionTable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/*
Added the test to provide additional test coverage for below mentioned issue
https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=42309
 */

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 10)
public class StudyDatasetFileFieldTest extends BaseWebDriverTest
{
    private final String IMPORT_PROJECT = "StudyDatasetFileFieldFolderImportProject";

    @BeforeClass
    public static void doSetup()
    {
        StudyDatasetFileFieldTest init = (StudyDatasetFileFieldTest) getCurrentTest();
        init.doCreateSteps();
    }

    @Nullable
    @Override
    protected String getProjectName()
    {
        return "Study Dataset File Field Project";
    }

    protected String getFolderName()
    {
        return "My Study";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    private void doCreateSteps()
    {
        _containerHelper.createProject(getProjectName(), "Study");
        clickButton("Create Study");
        clickButton("Create Study");
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
        _containerHelper.deleteProject(IMPORT_PROJECT, afterTest);
    }

    @Test
    public void testFileField() throws IOException
    {
        String datasetName = "Dataset-1";
        File inputFile = TestFileUtils.getSampleData("fileTypes/sample.txt"); //random file
        goToProjectHome();
        createDataset(datasetName);

        DatasetInsertPage insertDataPage = _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickViewData()
                .insertDatasetRow();

        insertDataPage.insert(Map.of("ParticipantId", "1",
                "SequenceNum", "2",
                "date", "2020-08-04",
                "fileField", inputFile.toString(),
                "textField", "Hello World..!",
                "intField", "25"));

        log("Edit the dataset");
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        table.clickEditRow(0);
        setFormElement(Locator.name("quf_textField"), "Welcome..!");
        checker().verifyTrue("File is not present ",  isElementPresent(Locator.linkContainingText("remove")));
        clickButton("Submit");

        log("Verify file field is not deleted after edit");
        File downloadedFile = doAndWaitForDownload(() -> waitAndClick(WAIT_FOR_JAVASCRIPT, Locator.tagWithAttribute("a", "title", "Download attached file"), 0));
        checker().verifyTrue("Incorrect file name ", FileUtils.contentEquals(downloadedFile, inputFile));

        goToFolderManagement().goToExportTab();
        new Checkbox(Locator.tagWithText("label", "Files").precedingSibling("input").findElement(getDriver())).check();
        File exportedFolderFile = doAndWaitForDownload(()->findButton("Export").click());

        log("Create a simple project as the import target.");
        _containerHelper.createProject(IMPORT_PROJECT, null);
        goToProjectHome(IMPORT_PROJECT);
        log("Import the folder.");
        importFolderFromZip(exportedFolderFile);

        log("Validate that the dataset has been imported as expected.");
        goToProjectHome(IMPORT_PROJECT);

        _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickViewData();

        assertElementPresent("Did not find the expected sample.txt from the imported dataset.", Locator.tagWithText("a", " datasetdata/sample.txt"), 1);
        downloadedFile = doAndWaitForDownload(() -> waitAndClick(WAIT_FOR_JAVASCRIPT, Locator.tagWithAttribute("a", "title", "Download attached file"), 0));
        checker().verifyTrue("Incorrect file content ", FileUtils.contentEquals(downloadedFile, inputFile));

    }

    protected void createDataset(String name)
    {
        DatasetDesignerPage definitionPage = _studyHelper.goToManageDatasets()
                .clickCreateNewDataset()
                .setName(name);

        DomainFormPanel panel = definitionPage.getFieldsPanel();
        panel.manuallyDefineFields("textField");
        panel.addField(new FieldDefinition("intField", FieldDefinition.ColumnType.Integer));
        panel.addField(new FieldDefinition("fileField", FieldDefinition.ColumnType.File));
        definitionPage.clickSave();
    }
}
