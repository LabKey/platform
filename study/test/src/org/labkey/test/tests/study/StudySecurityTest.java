/*
 * Copyright (c) 2016-2019 LabKey Corporation
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

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyC;
import org.labkey.test.pages.study.StudySecurityPage;
import org.labkey.test.pages.study.StudySecurityPage.GroupSecuritySetting;
import org.labkey.test.pages.study.StudySecurityPage.DatasetRoles;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PortalHelper;
import org.openqa.selenium.WebElement;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Category({DailyC.class})
@BaseWebDriverTest.ClassTimeout(minutes = 15)
public class StudySecurityTest extends BaseWebDriverTest
{
    // This must be the folder name because of the test data "studies/StudySecurityProject.folder.zip".
    private static final String FOLDER_NAME = "My Study";

    private static final String READER = "dsreader@studysecurity.test";
    private static final String EDITOR = "dseditor@studysecurity.test";
    private static final String LIMITED = "dslimited@studysecurity.test";
    private static final String NONE = "dsnone@studysecurity.test";

    private static final String GROUP_DEVELOPER = "Developers";
    private static final String GROUP_GUESTS = "Guests";
    private static final String GROUP_ALL_USERS = "All site users";
    private static final String GROUP_USERS = "Users";

    private static final String GROUP_READERS = "Readers";
    private static final String GROUP_EDITORS = "Editors";
    private static final String GROUP_LIMITED = "The Limited";
    private static final String GROUP_NONE = "No Access";

    // This study has about 50 datasets, won't check them all, limit to a few we are interested in.
    private static final String DS_ALT_ID = "Alt ID mapping";
    private static final String DS_COHORT = "EVC-1: Enrollment Vaccination";
    private static final String DS_DEMO = "DEM-1: Demographics";
    private static final String DS_FOLLOW_UP = "CPF-1: Follow-up Chemistry Panel";
    private static final String DS_TYPES = "Types";

    private static final String INSERT_BUTTON_TEXT = "Insert data";

    @BeforeClass
    public static void doSetup()
    {

        // Migrated this test from StudyBaseTest to BaseWebDriverTest, and much of the setup code below came from StudyBaseTest.

        StudySecurityTest initTest = (StudySecurityTest)getCurrentTest();

        initTest._containerHelper.createProject(initTest.getProjectName(), null);
        initTest._containerHelper.createSubfolder(initTest.getProjectName(), FOLDER_NAME, "Study");

        initTest._containerHelper.enableModule("Specimen");

        initTest.goToFolderManagement();
        initTest.clickAndWait(Locator.linkWithText("Folder Type"));

        // Activate specimen module to enable specimen UI/webparts
        initTest.checkCheckbox(Locator.checkboxByTitle("Specimen"));

        initTest.clickButton("Update Folder");

        new PortalHelper(initTest.getDriver()).doInAdminMode(portalHelper -> {
            portalHelper.addWebPart("Data Pipeline");
            portalHelper.addWebPart("Datasets");
            portalHelper.addWebPart("Specimens");
            portalHelper.addWebPart("Views");
        });

        initTest.clickFolder(FOLDER_NAME);

        initTest._userHelper.createUser(READER);
        initTest._userHelper.createUser(EDITOR);
        initTest._userHelper.createUser(LIMITED);
        initTest._userHelper.createUser(NONE);

        initTest.log("Import new study with alt-ID");
        initTest.importFolderFromZip(TestFileUtils.getSampleData("studies/AltIdStudy.folder.zip"));

        initTest.clickProject(initTest.getProjectName());
        initTest.importFolderFromZip(TestFileUtils.getSampleData("studies/StudySecurityProject.folder.zip"));

    }

    @Test
    public void testHappyPath()
    {
        log("Verify system admins (current user) should be able to edit everything and setup the pipeline.");
        Map<String, DatasetRoles> expectedDatasetRoles = Map.of(DS_FOLLOW_UP, DatasetRoles.EDITOR,
                                                                DS_DEMO, DatasetRoles.EDITOR,
                                                                DS_TYPES, DatasetRoles.EDITOR);
        verifyPermissions(expectedDatasetRoles, true);

        log(String.format("Group '%s' should be able to see all datasets, but not edit anything and not do anything with the pipeline.", GROUP_READERS));
        expectedDatasetRoles = Map.of(DS_FOLLOW_UP, DatasetRoles.READER,
                                    DS_DEMO, DatasetRoles.READER,
                                    DS_TYPES, DatasetRoles.READER);
        impersonate(READER);
        verifyPermissions(expectedDatasetRoles, false);
        stopImpersonating();

        log(String.format("Group '%s' should be able to see all datasets and edit them and import new data via the pipeline, but not set the pipeline path.", GROUP_EDITORS));
        expectedDatasetRoles = Map.of(DS_FOLLOW_UP, DatasetRoles.EDITOR,
                                    DS_DEMO, DatasetRoles.EDITOR,
                                    DS_TYPES, DatasetRoles.EDITOR);
        impersonate(EDITOR);
        verifyPermissions(expectedDatasetRoles, false);
        stopImpersonating();

        log(String.format("Group '%s' should be able to see only the few datasets we granted them and not do anything with the pipeline.", GROUP_LIMITED));
        expectedDatasetRoles = Map.of(DS_DEMO, DatasetRoles.READER,
                                    DS_TYPES, DatasetRoles.READER,
                                    DS_FOLLOW_UP, DatasetRoles.NONE);
        impersonate(LIMITED);
        verifyPermissions(expectedDatasetRoles, false);
        stopImpersonating();

        log(String.format("Group '%s' should not be able to see any datasets nor the pipeline.", GROUP_NONE));
        expectedDatasetRoles = Map.of(DS_FOLLOW_UP, DatasetRoles.NONE,
                                    DS_DEMO, DatasetRoles.NONE,
                                    DS_TYPES, DatasetRoles.NONE);
        impersonate(NONE);
        verifyPermissions(expectedDatasetRoles, false);
        stopImpersonating();

    }

    @Test
    public void testChangePermissions()
    {

        log(String.format("Revoke %s permissions to none.", GROUP_LIMITED));
        adjustGroupDatasetPerms(GROUP_LIMITED, GroupSecuritySetting.NONE);
        Map<String, DatasetRoles> expectedDatasetRoles = Map.of(DS_FOLLOW_UP, DatasetRoles.NONE,
                                                                DS_DEMO, DatasetRoles.NONE,
                                                                DS_TYPES, DatasetRoles.NONE);
        impersonate(LIMITED);
        verifyPermissions(expectedDatasetRoles, false);
        stopImpersonating();

        log(String.format("Reinstate read permission to %s to verify that per-dataset settings were preserved.", GROUP_LIMITED));
        adjustGroupDatasetPerms(GROUP_LIMITED, GroupSecuritySetting.PER_DATASET);
        expectedDatasetRoles = Map.of(DS_FOLLOW_UP, DatasetRoles.NONE,
                                    DS_DEMO, DatasetRoles.READER,
                                    DS_TYPES, DatasetRoles.READER);
        impersonate(LIMITED);
        verifyPermissions(expectedDatasetRoles, false);
        stopImpersonating();

        log(String.format("Move %s to per-dataset and grant edit only to dataset %s.", GROUP_EDITORS, DS_TYPES));

        Map<String, DatasetRoles> datasetRoles = Map.of(DS_TYPES, DatasetRoles.EDITOR);
        adjustGroupDatasetPerms(GROUP_EDITORS, GroupSecuritySetting.PER_DATASET, datasetRoles);

        expectedDatasetRoles = Map.of(DS_FOLLOW_UP, DatasetRoles.NONE,
                DS_DEMO, DatasetRoles.NONE,
                DS_TYPES, DatasetRoles.EDITOR);
        impersonate(EDITOR);
        verifyPermissions(expectedDatasetRoles, false);
        stopImpersonating();

        log(String.format("Reset %s to general edit.", GROUP_EDITORS));
        adjustGroupDatasetPerms(GROUP_EDITORS, GroupSecuritySetting.EDIT_ALL);
        expectedDatasetRoles = Map.of(DS_FOLLOW_UP, DatasetRoles.EDITOR,
                DS_DEMO, DatasetRoles.EDITOR,
                DS_TYPES, DatasetRoles.EDITOR);
        impersonate(EDITOR);
        verifyPermissions(expectedDatasetRoles, false);
        stopImpersonating();
    }

    protected void adjustGroupDatasetPerms(String groupName, GroupSecuritySetting setting)
    {
        adjustGroupDatasetPerms(groupName, setting, null);
    }

    protected void adjustGroupDatasetPerms(String groupName, GroupSecuritySetting setting, Map<String, DatasetRoles> datasetRolesMap)
    {
        navigateToFolder(getProjectName(), FOLDER_NAME);
        StudySecurityPage studySecurityPage = _studyHelper.enterStudySecurity();

        studySecurityPage.setGroupStudySecurity(groupName, setting);
        studySecurityPage.updateGroupSecurity();

        if (null != datasetRolesMap)
        {
            studySecurityPage.setDatasetPermissionsAndSave(groupName, datasetRolesMap);
            studySecurityPage.saveDatasetPermissions();
        }

        clickFolder(FOLDER_NAME);
    }

    private void verifyPermissions(Map<String, DatasetRoles> expectedDatasetRoles, boolean canSetupPipeline)
    {

        navigateToFolder(getProjectName(), FOLDER_NAME);

        verifyPipelinePermissions(canSetupPipeline);

        for(Map.Entry<String, DatasetRoles> entry : expectedDatasetRoles.entrySet())
        {

            String datasetName = entry.getKey();

            WebElement datasetLink = Locator.linkWithText(datasetName).findWhenNeeded(getDriver());

            if(entry.getValue().equals(DatasetRoles.NONE))
            {
                checker().verifyFalse(
                        String.format("The user does not have permissions to dataset '%s' but the link is visible.",
                                datasetName),
                        datasetLink.isDisplayed());
            }
            else
            {
                // If the link to the dataset is not visible, and it should be, do not check anything else.
                if (checker().verifyTrue(
                        String.format("The user has permissions to dataset '%s' but the link is not visible.",
                                datasetName),
                        datasetLink.isDisplayed()))
                {

                    log(String.format("Go to dataset '%s' and verify the user has the expected permissions.", datasetName));
                    clickAndWait(datasetLink);

                    // Make sure the link took us to the expected dataset.
                    WebElement header = Locator.tagWithClassContaining("div", "lk-body-title")
                            .child(Locator.xpath(String.format("h3[contains(text(), '%s')]", datasetName)))
                            .findWhenNeeded(getDriver());

                    if (checker()
                            .withScreenshot(String.format("NotAtDataset_%s", datasetName))
                            .verifyTrue(
                                    String.format("Doesn't look like we are at '%s' dataset.", datasetName),
                                    header.isDisplayed()))
                    {


                        verifyDataRegionTable(datasetName, entry.getValue());

                    }

                }

                // Go back to the 'Overview' page and check the expected permissions of the next dataset in the list.
                clickFolder(FOLDER_NAME);

            }

        }

    }

    private void verifyPipelinePermissions(boolean canSetupPipeline)
    {
        WebElement dataPipelinePanel = Locator.tagWithClassContaining("div", "panel-body").findElements(getDriver()).get(1);
        WebElement setupButton = Locator.lkButton("Setup").findWhenNeeded(dataPipelinePanel);
        checker().verifyEquals("Pipeline 'Setup' button visibility not as expected.", canSetupPipeline, setupButton.isDisplayed());
    }

    private void verifyDataRegionTable(String datasetName, DatasetRoles datasetRole)
    {
        DataRegionTable dataRegionTable = new DataRegionTable("Dataset", getDriver());
        List<WebElement> updateLinks = DataRegionTable.updateLinkLocator().findElements(getDriver());

        if(datasetRole.equals(DatasetRoles.READER))
        {
            checker()
                    .verifyFalse(
                            String.format("The user should only have read permissions to dataset '%s' but the 'insert button' is present.",
                                    datasetName),
                            dataRegionTable.hasHeaderMenu(INSERT_BUTTON_TEXT));

            checker()
                    .verifyEquals(
                            String.format("User should not be able to edit rows in '%s' but update link is present.",
                                    datasetName),
                            0, updateLinks.size());

            checker().screenShotIfNewError(String.format("ReaderHasUpdate_%s", datasetName));
        }
        else
        {

            verifyInsertPermissions(dataRegionTable, datasetName);

            if(datasetRole.equals(DatasetRoles.EDITOR))
            {
                log("For the 'editor' role each one of the entries in the dataset should be editable.");
                checker().verifyEquals("Entries in the dataset should be editable.", dataRegionTable.getDataRowCount(), updateLinks.size());
            }
            else
            {
                log("For the 'reader' or 'author' roles none of the entries in the dataset should be editable.");
                checker().verifyEquals("Entries in the dataset appear to be editable.", 0, updateLinks.size());
            }

        }
    }

    private void verifyInsertPermissions(DataRegionTable dataRegionTable, String datasetName)
    {
        if(checker()
                .verifyTrue(
                        String.format("The user should have insert permissions to dataset '%s' but the 'insert button' is not present.",
                                datasetName),
                        dataRegionTable.hasHeaderMenu(INSERT_BUTTON_TEXT)))
        {

            // Check the text on the insert menu.
            dataRegionTable.clickHeaderButton(INSERT_BUTTON_TEXT);

            List<String> expectedMenuText = Arrays.asList(DataRegionTable.getInsertNewButtonText(), DataRegionTable.getImportBulkDataText());
            List<String> actualMenuText = getTexts(
                    Locator.xpath(
                            "//div[contains(@class, 'lk-menu-drop')][contains(@class, 'open')]//li//a")
                            .findElements(getDriver()));

            Collections.sort(expectedMenuText);
            Collections.sort(actualMenuText);

            checker().verifyEquals("Insert menus not as expected.", expectedMenuText, actualMenuText);

            dataRegionTable.clickHeaderButton(INSERT_BUTTON_TEXT);

        }
    }

    @Before
    public void setTestDefaultStudySecurity()
    {

        StudySecurityPage studySecurityPage = goToStudySecurityPage();

        studySecurityPage.setSecurityTypeAndUpdate(StudySecurityPage.StudySecurityType.ADVANCED_WRITE);

        Map<String, GroupSecuritySetting> groupSettings = Map.of(GROUP_READERS, GroupSecuritySetting.READ_ALL,
                GROUP_EDITORS, GroupSecuritySetting.EDIT_ALL,
                GROUP_LIMITED, GroupSecuritySetting.PER_DATASET,
                GROUP_NONE, GroupSecuritySetting.NONE,
                GROUP_DEVELOPER, GroupSecuritySetting.NONE,
                GROUP_GUESTS, GroupSecuritySetting.NONE,
                GROUP_ALL_USERS, GroupSecuritySetting.NONE,
                GROUP_USERS, GroupSecuritySetting.NONE);

        studySecurityPage.setGroupStudySecurityAndUpdate(groupSettings);

        // Clear any previous per dataset permissions.
        studySecurityPage.clearAll();

        // Grant reader rights to the LIMITED group for a couple of datasets.
        Map<String, DatasetRoles> permissions = Map.of(DS_DEMO, DatasetRoles.READER,
                                                DS_TYPES, DatasetRoles.READER);

        studySecurityPage.setDatasetPermissionsAndSave(GROUP_LIMITED, permissions);

    }

    private StudySecurityPage goToStudySecurityPage()
    {
        StudySecurityPage studySecurityPage;
        if(!getCurrentRelativeURL().toLowerCase().contains("study-security-begin.view?"))
        {
            goToProjectFolder(getProjectName(), FOLDER_NAME);
            studySecurityPage = _studyHelper.enterStudySecurity();
            waitForElement(Locator.name("securityString"));
        }
        else
        {
            studySecurityPage = new StudySecurityPage(getDriver());
        }

        return studySecurityPage;
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
        _userHelper.deleteUsers(false, READER, EDITOR, LIMITED, NONE);
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    @Override
    protected String getProjectName()
    {
        return "Study_Security_Test";
    }

}
