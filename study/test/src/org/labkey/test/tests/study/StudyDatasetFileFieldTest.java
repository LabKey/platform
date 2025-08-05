package org.labkey.test.tests.study;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.Daily;
import org.labkey.test.components.domain.DomainFormPanel;
import org.labkey.test.components.ext4.Checkbox;
import org.labkey.test.pages.DatasetInsertPage;
import org.labkey.test.pages.ImportDataPage;
import org.labkey.test.pages.ViewDatasetDataPage;
import org.labkey.test.pages.study.DatasetDesignerPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.AuditLogHelper;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.DomainUtils;
import org.labkey.test.util.TestDataGenerator;
import org.labkey.test.util.FileBrowserHelper;
import org.labkey.test.util.PasswordUtil;
import org.labkey.test.util.data.TestDataUtils;
import org.openqa.selenium.NoSuchElementException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/*
Added the test to provide additional test coverage for below mentioned issue
https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=42309
 */

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 10)
public class StudyDatasetFileFieldTest extends BaseWebDriverTest
{
    private static final String EXCLUDED_CHARS = "\""; // this gets encoded as %22 when the form data is sent.
    private static final String IMPORT_PROJECT = "StudyDatasetFileFieldFolderImportProject";
    private static final String FILE_FIELD_1 = TestDataGenerator.randomFieldName("File Field 1", EXCLUDED_CHARS, DomainUtils.DomainKind.StudyDatasetDate);
    private static final String FILE_FIELD_2 = TestDataGenerator.randomFieldName("File Field 2", EXCLUDED_CHARS, DomainUtils.DomainKind.StudyDatasetDate);
    private static final String INT_FIELD = TestDataGenerator.randomFieldName("Int Field", EXCLUDED_CHARS, DomainUtils.DomainKind.StudyDatasetDate);
    private static final String TEXT_FIELD = TestDataGenerator.randomFieldName("Text Field", EXCLUDED_CHARS, DomainUtils.DomainKind.StudyDatasetDate);

    @BeforeClass
    public static void doSetup()
    {
        StudyDatasetFileFieldTest init = getCurrentTest();
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
        _containerHelper.deleteProject(IMPORT_PROJECT, false);
    }

    @Test
    public void testFileField() throws IOException, CommandException
    {
        new ApiPermissionsHelper(this)
                .setSiteRoleUserPermissions(PasswordUtil.getUsername(), "See Absolute File Paths");

        String datasetName = "Dataset-1";
        File inputFile = TestFileUtils.getSampleData("fileTypes/sample.txt"); //arbitrary file
        goToProjectHome();
        createDataset(datasetName);

        DatasetInsertPage insertDataPage = _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickViewData()
                .insertDatasetRow();

        insertDataPage.insert(Map.of("ParticipantId", "1",
                "SequenceNum", "2",
                "date", "2020-08-04",
                FILE_FIELD_1, inputFile.toString(),
                TEXT_FIELD, "Hello World..!",
                INT_FIELD, "25"));
        verifyFileAuditLogs(List.of(Map.of(
                AuditLogHelper.COL_FILE_AUDIT_FILE, inputFile.getName(),
                AuditLogHelper.COL_FILE_AUDIT_PROVIDED_FILE, inputFile.getName()
        )));
        log("Edit the dataset");
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        table.clickEditRow(0);
        setFormElement(Locator.name("quf_" + TEXT_FIELD), "Welcome..!");
        checker().verifyTrue("File is not present ",  isElementPresent(Locator.linkContainingText("remove")));
        clickButton("Submit");

        log("Verify file field is not deleted after edit");
        File downloadedFile = doAndWaitForDownload(() -> waitAndClick(WAIT_FOR_JAVASCRIPT, Locator.tagWithAttribute("a", "title", "Download attached file"), 0));
        checker().verifyTrue("Incorrect file name ", FileUtils.contentEquals(downloadedFile, inputFile));

        FileBrowserHelper.FileDetailInfo fileInfoOriginalFile = _fileBrowserHelper.getFileDetailInfo(getProjectName(), "sample.txt");

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

        String expectedText;

        if (SystemUtils.IS_OS_WINDOWS)
        {
            expectedText = "datasetdata\\sample.txt";
        }
        else
        {
            expectedText = "datasetdata/sample.txt";
        }

        assertElementPresent("Did not find the expected sample.txt from the imported dataset.", Locator.tagContainingText("a", expectedText), 1);
        downloadedFile = doAndWaitForDownload(() -> waitAndClick(WAIT_FOR_JAVASCRIPT, Locator.tagWithAttribute("a", "title", "Download attached file"), 0));
        checker().verifyTrue("Incorrect file content ", FileUtils.contentEquals(downloadedFile, inputFile));

        log("Update with validation error, reshow test.");
         _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickViewData();

        table = new DataRegionTable("Dataset", getDriver());
        table.clickEditRow(0);
        checker().verifyTrue("File is not present ",  isElementPresent(Locator.linkContainingText("remove")));
        setFormElement(Locator.name("quf_" + INT_FIELD), "NOT A NUMBER");
        clickButton("Submit");

        // assert correct reshow with error
        assertTextPresent("Could not convert value:");
        checker().verifyTrue("File is not present ",  isElementPresent(Locator.linkContainingText("remove")));

        // Issue : 53320. Update a file field with a different file
        click(Locator.linkContainingText("remove"));
        File updateFile = TestFileUtils.getSampleData("fileTypes/pdf_sample.pdf");
        setFormElement(Locator.name("quf_" + INT_FIELD), "2");
        setFormElement(Locator.name("quf_" + FILE_FIELD_1), updateFile.toString());
        clickButton("Submit");

        FileBrowserHelper.FileDetailInfo fileInfoImportedFile = _fileBrowserHelper.getFileDetailInfo(IMPORT_PROJECT, "sample.txt");

        // error case: import, update, merge with invalid file path
        ViewDatasetDataPage datasetDataPage = new ViewDatasetDataPage(getDriver());
        ImportDataPage importDataPage = datasetDataPage.importBulkData();
        importDataPage.setCopyPasteMerge(false, false);
        importFilePathError("badNew", "101", fileInfoOriginalFile.absoluteFilePath());
        importFilePathError("badNew", "101", fileInfoOriginalFile.dataFileUrl());
        importFilePathError("badNew", "101", fileInfoOriginalFile.webDavUrl());
        importFilePathError("badNew", "101", "bad.txt");
        importFilePathError("badNew", "101", fileInfoOriginalFile.absoluteFilePath().replace("sample.txt", ""));
        importFilePathError("badNew", "101", ".");
        importFilePathError("badNew", "101", "../..");
        importDataPage.setCopyPasteMerge(true, true);
        importFilePathError("badNew", "101", fileInfoOriginalFile.absoluteFilePath());
        importFilePathError("1", "2", fileInfoOriginalFile.dataFileUrl());
        importFilePathError("badNew", "101", fileInfoOriginalFile.webDavUrl());
        importFilePathError("1", "2", "bad.txt");
        importFilePathError("badNew", "101", fileInfoOriginalFile.absoluteFilePath().replace("sample.txt", ""));
        importFilePathError("1", "2", ".");
        importFilePathError("badNew", "101", "../..");
        importDataPage.setCopyPasteMerge(false, true);
        importFilePathError("1", "2", fileInfoOriginalFile.absoluteFilePath());
        importFilePathError("1", "2", fileInfoOriginalFile.dataFileUrl());
        importFilePathError("1", "2", fileInfoOriginalFile.webDavUrl());
        importFilePathError("1", "2", "bad.txt");
        importFilePathError("1", "2", fileInfoOriginalFile.absoluteFilePath().replace("sample.txt", ""));
        importFilePathError("1", "2", ".");
        importFilePathError("1", "2", "../..");
        // happy case, import/update/merge with valid file path
        importDataPage.setCopyPasteMerge(false, false);
        String header = "ParticipantId\tSequenceNum\tfileField\n";
        String data =  "2\t3\t" + fileInfoImportedFile.absoluteFilePath() + "\n" +
                "3\t4\t" + fileInfoImportedFile.dataFileUrl() + "\n" +
                "4\t5\t" + fileInfoImportedFile.webDavUrl() + "\n" +
                "5\t6\t" + fileInfoImportedFile.webDavUrlRelative() + "\n";
        setFormElement(Locator.name("text"), header + data);
        new ImportDataPage(getDriver()).submit();
        datasetDataPage = new ViewDatasetDataPage(getDriver());
        importDataPage = datasetDataPage.importBulkData();
        importDataPage.setCopyPasteMerge(false, true);
        data =  "2\t3\t" + fileInfoImportedFile.dataFileUrl() + "\n" +
                "3\t4\t" + fileInfoImportedFile.webDavUrl() + "\n" +
                "4\t5\t" + fileInfoImportedFile.webDavUrlRelative() + "\n" +
                "5\t6\t" + fileInfoImportedFile.absoluteFilePath() + "\n";
        setFormElement(Locator.name("text"), header + data);
        new ImportDataPage(getDriver()).submit();
        datasetDataPage = new ViewDatasetDataPage(getDriver());
        importDataPage = datasetDataPage.importBulkData();
        importDataPage.setCopyPasteMerge(true, true);
        data += "6\t7\t" + fileInfoImportedFile.webDavUrlRelative() + "\n";
        setFormElement(Locator.name("text"), header + data);
        new ImportDataPage(getDriver()).submit();
    }

    private void importFilePathError(String participantId, String sequenceNum, String filePath)
    {
        String pasteData = TestDataUtils.tsvStringFromRowMapsEscapeBackslash(List.of(
                Map.of("ParticipantId", participantId, "SequenceNum", sequenceNum, FILE_FIELD_1, filePath)),
                List.of("ParticipantId", "SequenceNum", FILE_FIELD_1), true);
        setFormElement(Locator.name("text"), pasteData);
        new ImportDataPage(getDriver()).submitExpectingError();
        try
        {
            waitForElementToBeVisible(Locator.xpath("//div[contains(@class, 'labkey-error')][contains(text(),'Invalid file path: " + filePath + "')]"));
        }
        catch(NoSuchElementException nse)
        {
            checker().fatal().error("Invalid file path error not present.");
        }
    }

    @Test
    public void testFileRenamingAndAuditing() throws IOException, CommandException
    {
        String datasetName = "Dataset-Multi-File";
        File inputFile = TestFileUtils.getSampleData("fileTypes/csv_sample.csv");
        goToProjectHome();
        createDataset(datasetName);

        DatasetInsertPage insertDataPage = _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickViewData()
                .insertDatasetRow();

        insertDataPage.insert(Map.of("ParticipantId", "1",
                "SequenceNum", "2",
                "date", "2020-08-04",
                FILE_FIELD_1, inputFile.toString(),
                FILE_FIELD_2, inputFile.toString(),
                INT_FIELD, "26"));
        verifyFileAuditLogs(List.of(
                Map.of(
                    AuditLogHelper.COL_FILE_AUDIT_FILE, "csv_sample-1.csv",
                    AuditLogHelper.COL_FILE_AUDIT_PROVIDED_FILE, "csv_sample.csv"
                ),
                Map.of(
                        AuditLogHelper.COL_FILE_AUDIT_FILE, inputFile.getName(),
                        AuditLogHelper.COL_FILE_AUDIT_PROVIDED_FILE, inputFile.getName()
                )
        ));

        insertDataPage = _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickViewData()
                .insertDatasetRow();
        insertDataPage.insert(Map.of("ParticipantId", "1",
                "SequenceNum", "3",
                "date", "2020-08-04",
                FILE_FIELD_2, inputFile.toString(),
                INT_FIELD, "27"));
        verifyFileAuditLogs(List.of(
                Map.of(
                    AuditLogHelper.COL_FILE_AUDIT_FILE, "csv_sample-2.csv",
                    AuditLogHelper.COL_FILE_AUDIT_PROVIDED_FILE, "csv_sample.csv"
                )
        ));
    }

    protected void verifyFileAuditLogs(List<Map<String, Object>> expectedFileData) throws IOException, CommandException
    {
        List<String> columnNames = expectedFileData.get(0).keySet().stream().map(Object::toString).toList();
        AuditLogHelper auditLogHelper = new AuditLogHelper(this, () -> WebTestHelper.getRemoteApiConnection(false));
        List<Map<String, Object>> events = auditLogHelper.getAuditLogsFromLKS(getProjectName(), AuditLogHelper.AuditEvent.FILE_SYSTEM_EVENT, columnNames, null, expectedFileData.size(), null).getRows();
        assertEquals("Number of file audit log events not as expected", expectedFileData.size(), events.size());
        for (int i = 0; i < expectedFileData.size(); i++)
        {
            for (String key : columnNames)
                assertEquals("Event value for " + key + " not as expected", expectedFileData.get(i).get(key), events.get(i).get(key));
        }
    }

    protected void createDataset(String name)
    {
        DatasetDesignerPage definitionPage = _studyHelper.goToManageDatasets()
                .clickCreateNewDataset()
                .setName(name);

        DomainFormPanel panel = definitionPage.getFieldsPanel();
        panel.manuallyDefineFields(TEXT_FIELD);
        panel.addField(new FieldDefinition(INT_FIELD, FieldDefinition.ColumnType.Integer));
        panel.addField(new FieldDefinition(FILE_FIELD_1, FieldDefinition.ColumnType.File));
        panel.addField(new FieldDefinition(FILE_FIELD_2, FieldDefinition.ColumnType.File));
        definitionPage.clickSave();
    }
}
