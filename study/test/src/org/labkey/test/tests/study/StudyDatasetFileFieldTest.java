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
package org.labkey.test.tests.study;

import org.apache.commons.io.FileUtils;
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
import org.labkey.test.pages.query.UpdateQueryRowPage;
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

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 10)
public class StudyDatasetFileFieldTest extends BaseWebDriverTest
{
    private static final String EXCLUDED_CHARS = "\""; // this gets encoded as %22 when the form data is sent.
    private static final String IMPORT_PROJECT = "StudyDatasetFileFieldFolderImportProject";
    // Include a "\" character at the end of the file field name to verify it round trips with escaped form field characters
    private static final String FILE_FIELD_1 = TestDataGenerator.randomFieldName("File Field 1", 5, 20, EXCLUDED_CHARS, DomainUtils.DomainKind.StudyDatasetDate, 120) + "\\";
    private static final String FILE_FIELD_2 = TestDataGenerator.randomFieldName("File Field 2", 5, 20, EXCLUDED_CHARS, DomainUtils.DomainKind.StudyDatasetDate, 120);
    private static final String INT_FIELD = TestDataGenerator.randomFieldName("Int Field", 5, 20, EXCLUDED_CHARS, DomainUtils.DomainKind.StudyDatasetDate, 120);
    private static final String TEXT_FIELD = TestDataGenerator.randomFieldName("Text Field", 5, 20, EXCLUDED_CHARS, DomainUtils.DomainKind.StudyDatasetDate, 120);

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

    @Test // Issue 42309
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
        UpdateQueryRowPage updatePage = new DataRegionTable("Dataset", getDriver())
                .clickEditRow(0)
                .setField(TEXT_FIELD, "Welcome..!");
        checker().verifyTrue("File is not present ", isElementPresent(Locator.linkContainingText("remove")));
        updatePage.submit();

        log("Verify file field is not deleted after edit");
        File downloadedFile = doAndWaitForDownload(() -> waitAndClick(WAIT_FOR_JAVASCRIPT, Locator.tagWithAttribute("a", "title", "Download attached file"), 0));
        checker().verifyTrue("Incorrect file name ", FileUtils.contentEquals(downloadedFile, inputFile));

        FileBrowserHelper.FileDetailInfo fileInfoOriginalFile = FileBrowserHelper.getFileDetailInfo(getProjectName(), "sample.txt");

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

        String expectedText = "sample.txt";

        assertElementPresent("Did not find the expected sample.txt from the imported dataset.", Locator.tagContainingText("a", expectedText), 1);
        downloadedFile = doAndWaitForDownload(() -> waitAndClick(WAIT_FOR_JAVASCRIPT, Locator.tagWithAttribute("a", "title", "Download attached file"), 0));
        checker().verifyTrue("Incorrect file content ", FileUtils.contentEquals(downloadedFile, inputFile));

        log("Update with validation error, reshow test.");
         _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickViewData();

        updatePage = new DataRegionTable("Dataset", getDriver())
                .clickEditRow(0);
        checker().verifyTrue("File is not present ", isElementPresent(Locator.linkContainingText("remove")));
        updatePage.setField(INT_FIELD, "NOT A NUMBER");
        updatePage.submitExpectingError();

        // assert correct reshow with error
        assertTextPresent("Could not convert value:");
        checker().verifyTrue("File is not present ", isElementPresent(Locator.linkContainingText("remove")));

        // Issue 53320: Update a file field with a different file
        click(Locator.linkContainingText("remove"));
        File updateFile = TestFileUtils.getSampleData("fileTypes/pdf_sample.pdf");
        updatePage.setField(INT_FIELD, "2");
        updatePage.setField(FILE_FIELD_1, updateFile);
        updatePage.submit();

        FileBrowserHelper.FileDetailInfo fileInfoImportedFile = FileBrowserHelper.getFileDetailInfo(IMPORT_PROJECT, "sample.txt");

        // error case: import, update, merge with an invalid file path
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
        // TODO: This should verify the rows in the data region after import
        importDataPage.setCopyPasteMerge(false, false);
        String header = "ParticipantId\tSequenceNum\t" + FILE_FIELD_1 + "\n";
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
        String pasteData = TestDataUtils.tsvStringFromRowMaps(List.of(
                Map.of("ParticipantId", participantId, "SequenceNum", sequenceNum, FILE_FIELD_1, filePath)),
                List.of("ParticipantId", "SequenceNum", FILE_FIELD_1), true);
        setFormElement(Locator.name("text"), pasteData);
        new ImportDataPage(getDriver()).submitExpectingError();
        try
        {
            waitForElementToBeVisible(Locator.xpath("//div[contains(@class, 'labkey-error')][contains(text(),'Invalid file path: " + filePath + "')]"));
        }
        catch (NoSuchElementException nse)
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
        List<String> columnNames = expectedFileData.getFirst().keySet().stream().map(Object::toString).toList();
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
