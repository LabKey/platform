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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.study.StudySecurityPage;
import org.labkey.test.pages.study.StudySecurityPage.DatasetRoles;
import org.labkey.test.pages.study.StudySecurityPage.GroupSecuritySetting;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.openqa.selenium.Alert;
import org.openqa.selenium.WebElement;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 15)
public class StudySecurityTest extends BaseWebDriverTest
{
    // This must be the folder name because of the imported study from test data.
    private static final String FOLDER_NAME = "My Study";

    // These specific user account names are needed because of the imported study from test data.
    private static String USER_ADMIN;
    private static final String USER_READER = "dsreader@studysecurity.test";
    private static final String USER_EDITOR = "dseditor@studysecurity.test";
    private static final String USER_AUTHOR = "dsauthor@studysecurity.test";
    private static final String USER_LIMITED = "dslimited@studysecurity.test";
    private static final String USER_NONE = "dsnone@studysecurity.test";

    // These user groups are created with the imported study from test data.
    // These groups are already populated with the users listed above.
    private static final String GROUP_READERS = "Readers";
    private static final String GROUP_EDITORS = "Editors";
    private static final String GROUP_AUTHORS = "Authors";
    private static final String GROUP_LIMITED = "The Limited";
    private static final String GROUP_NONE = "No Access";

    // General system groups.
    private static final String GROUP_DEVELOPER = "Developers";
    private static final String GROUP_GUESTS = "Guests";
    private static final String GROUP_ALL_USERS = "All site users";
    private static final String GROUP_USERS = "Users";

    // This study has about 50 datasets, not going to use them all. Test is limited to a few we are interested in.
    private static final String DS_ALT_ID = "Alt ID mapping";
    private static final String DS_COHORT = "EVC-1: Enrollment Vaccination";
    private static final String DS_DEMO = "DEM-1: Demographics";
    private static final String DS_ENROLL = "ENR-1: Enrollment";
    private static final String DS_FOLLOW_UP = "CPF-1: Follow-up Chemistry Panel";
    private static final String DS_MISSED = "MV-1: Missed Visit";
    private static final String DS_TYPES = "Types";

    // User not part of the imported study, but used in one of the tests.
    private static final String USER_IN_TWO = "dsintwo@studysecurity.test";

    private static final String INSERT_BUTTON_TEXT = "Insert data";

    private static boolean initConditionsValid;

    @BeforeClass
    public static void doSetup()
    {

        USER_ADMIN = getCurrentTest().getCurrentUser();

        StudySecurityTest initTest = (StudySecurityTest)getCurrentTest();

        initTest._containerHelper.createProject(initTest.getProjectName(), null);
        initTest._containerHelper.createSubfolder(initTest.getProjectName(), FOLDER_NAME, "Study");

        initTest.clickFolder(FOLDER_NAME);

        initTest._userHelper.createUser(USER_READER);
        initTest._userHelper.createUser(USER_EDITOR);
        initTest._userHelper.createUser(USER_AUTHOR);
        initTest._userHelper.createUser(USER_LIMITED);
        initTest._userHelper.createUser(USER_NONE);
        initTest._userHelper.createUser(USER_IN_TWO);

        // This study file contains the subfolder and groups that are used in this tests.
        initTest.clickProject(initTest.getProjectName());
        initTest.importFolderFromZip(TestFileUtils.getSampleData("studies/StudySecurityProject.folder.zip"));

        initTest.log("Validate that the default settings are as expected.");
        initConditionsValid = initTest.verifyDefaultSettings();
    }

    // Not really sure how else to verify this. This simply validates the default setting of 'Study Security Type', so
    // it needs to be run before any other tests. Unfortunately I cannot just assert here because it looks like if it
    // fails it will stop all other tests from running.
    public boolean verifyDefaultSettings()
    {
        goToStudyFolder();
        StudySecurityPage studySecurityPage = _studyHelper.enterStudySecurity();
        return StudySecurityPage.StudySecurityType.BASIC_READ.equals(studySecurityPage.getSecurityType());
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
        _userHelper.deleteUsers(false, USER_READER, USER_EDITOR, USER_AUTHOR, USER_LIMITED, USER_NONE, USER_IN_TWO);
    }

    /**
     * Validate that the Study Security Type is set to 'basic read-only' by default.
     */
    @Test
    public void testDefaultSettings()
    {
        checker().verifyTrue("The default/initial setting for the 'Study Security Type' should be basic read-only, they were not.",
                initConditionsValid);
    }

    /**
     * Validate that the various parts of the Study Security page are shown and hidden as expected.
     */
    @Test
    public void testUI()
    {
        goToStudyFolder();
        StudySecurityPage studySecurityPage = _studyHelper.enterStudySecurity();

        log("Verify that the various 'Study Security Type' settings update the UI appropriately.");

        studySecurityPage.setSecurityTypeAndUpdate(StudySecurityPage.StudySecurityType.BASIC_READ);
        checker().verifyFalse("'Study Security' panel should not be visible with basic read-only security type.",
                studySecurityPage.isGroupStudySecurityVisible());
        checker().verifyFalse("'Per Dataset Permissions' panel should not be visible with basic read-only security type.",
                studySecurityPage.isDatasetPermissionPanelVisible());
        checker().screenShotIfNewError("testUI_Basic_Read-Only_Panels");

        studySecurityPage.setSecurityTypeAndUpdate(StudySecurityPage.StudySecurityType.BASIC_WRITE);
        checker().verifyFalse("'Study Security' panel should not be visible with basic write security type.",
                studySecurityPage.isGroupStudySecurityVisible());
        checker().verifyFalse("'Per Dataset Permissions' panel should not be visible with basic write security type.",
                studySecurityPage.isDatasetPermissionPanelVisible());
        checker().screenShotIfNewError("testUI_Basic_Write_Panels");

        studySecurityPage.setSecurityTypeAndUpdate(StudySecurityPage.StudySecurityType.ADVANCED_READ);
        checker().verifyTrue("'Study Security' panel should be visible with custom read-only security type, it is not.",
                studySecurityPage.isGroupStudySecurityVisible());
        checker().verifyTrue("'Per Dataset Permissions' panel should be visible with custom read-only security type, it is not.",
                studySecurityPage.isDatasetPermissionPanelVisible());
        checker().screenShotIfNewError("testUI_Custom_Read-Only_Panels");

        studySecurityPage.setSecurityTypeAndUpdate(StudySecurityPage.StudySecurityType.ADVANCED_WRITE);
        checker().verifyTrue("'Study Security' panel should be visible with custom write security type, it is not.",
                studySecurityPage.isGroupStudySecurityVisible());
        checker().verifyTrue("'Per Dataset Permissions' panel should be visible with custom write security type, it is not.",
                studySecurityPage.isDatasetPermissionPanelVisible());
        checker().screenShotIfNewError("testUI_Custom_Write_Panels");

        log("Verify that setting 'Study Security Type' to custom read-only does not show any update permissions.");

        studySecurityPage.setSecurityTypeAndUpdate(StudySecurityPage.StudySecurityType.ADVANCED_READ);

        // Need to make sure that one of the groups is set to Per Dataset.
        studySecurityPage.setGroupStudySecurityAndUpdate(Map.of(GROUP_LIMITED, GroupSecuritySetting.PER_DATASET));

        checker().verifyFalse("With custom read-only permissions edit all should not be a 'Study Security' option.",
                studySecurityPage.getAllowedGroupRoles().contains(GroupSecuritySetting.EDIT_ALL));

        List<DatasetRoles> expectedPermissions = Arrays.asList(DatasetRoles.NONE, DatasetRoles.READER);
        List<DatasetRoles> actualPermissions = studySecurityPage.getAllowedDatasetPermissions(GROUP_LIMITED);

        Collections.sort(expectedPermissions);
        Collections.sort(actualPermissions);
        checker().verifyEquals("The 'Per Dataset Permissions options not as expected for read-only.",
                expectedPermissions, actualPermissions);

        log("Verify that setting 'Study Security Type' to custom edit shows everything.");

        studySecurityPage.setSecurityTypeAndUpdate(StudySecurityPage.StudySecurityType.ADVANCED_WRITE);

        checker().verifyTrue("With custom edit permissions edit all should be a 'Study Security' option.",
                studySecurityPage.getAllowedGroupRoles().contains(GroupSecuritySetting.EDIT_ALL));

        log("Check that if no group is 'Per Dataset' the list of datasets is not shown.");
        Map<String, GroupSecuritySetting> clearAll = Map.of(GROUP_AUTHORS, GroupSecuritySetting.EDIT_ALL,
                                                            GROUP_EDITORS, GroupSecuritySetting.EDIT_ALL,
                                                            GROUP_NONE, GroupSecuritySetting.EDIT_ALL,
                                                            GROUP_READERS, GroupSecuritySetting.EDIT_ALL,
                                                            GROUP_LIMITED, GroupSecuritySetting.EDIT_ALL);
        studySecurityPage.setGroupStudySecurityAndUpdate(clearAll);
        checker().verifyFalse("The list of dataset in the 'Per Dataset Permissions' panel should not be visible if no group is Per Dataset.",
                studySecurityPage.isDatasetPermissionPanelVisible());

        log("Add a group to the 'Per Dataset' and validate that the options in the dropdown are as expected.");
        studySecurityPage.setGroupStudySecurityAndUpdate(Map.of(GROUP_AUTHORS, GroupSecuritySetting.PER_DATASET));

        expectedPermissions = Arrays.asList(DatasetRoles.NONE, DatasetRoles.READER, DatasetRoles.AUTHOR, DatasetRoles.EDITOR);
        actualPermissions = studySecurityPage.getAllowedDatasetPermissions(GROUP_AUTHORS);

        Collections.sort(expectedPermissions);
        Collections.sort(actualPermissions);
        checker().verifyEquals("The 'Per Dataset Permissions options not as expected for write.",
                expectedPermissions, actualPermissions);

        log("Verify that the highlights are appropriate for the datasets.");
        checker().verifyTrue(String.format("The dataset '%s' has alternative ID and should be highlighted, it is not.", DS_ALT_ID),
                studySecurityPage.isDatasetHighlighted(DS_ALT_ID));
        checker().verifyTrue(String.format("The dataset '%s' has cohorts to the study and should be highlighted, it is not.", DS_COHORT),
                studySecurityPage.isDatasetHighlighted(DS_COHORT));
        checker().verifyFalse(String.format("The dataset '%s' is not special and should not be highlighted.", DS_TYPES),
                studySecurityPage.isDatasetHighlighted(DS_TYPES));

        log("UI looks correct.");
    }

    /**
     * This is from the original version of this test (may not be needed?). Validate that only admins can set the pipeline for a study.
     */
    @Test
    public void testPipelinePermissions()
    {
        log("Verify that only an admin can set the pipeline on a study.");

        goToStudyFolder();

        WebElement dataPipelinePanel = Locator.tagWithClassContaining("div", "panel-body").findElements(getDriver()).get(1);
        WebElement setupButton = Locator.lkButton("Setup").findWhenNeeded(dataPipelinePanel);
        checker().verifyTrue("Pipeline 'Setup' button is not visible to an admin.", setupButton.isDisplayed());

        impersonate(USER_EDITOR);
        checker().verifyFalse("Pipeline 'Setup' button is visible to a non-admin user, it should not be.", setupButton.isDisplayed());
        stopImpersonating();
    }

    /**
     * Verify that Per Dataset Permissions take precedence over folder permissions. This is a user found issue
     * (<a href="https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=41202">
     *     Secure Issue 41202: Combination of Submitter and Reader Role does not see the "Insert Rows" icon</a>)
     */
    @Test
    public void testFolderVsPerDatasetPermissions()
    {

        log(String.format("Move the read-only folder group '%s' to be an editor on a dataset '%s'", GROUP_READERS, DS_DEMO));
        Map<String, DatasetRoles> expectedDatasetRoles = Map.of(DS_DEMO, DatasetRoles.EDITOR);
        adjustGroupDatasetPerms(GROUP_READERS, GroupSecuritySetting.PER_DATASET, expectedDatasetRoles);

        log(String.format("Impersonate user '%s' and validate that they have editor permissions.", USER_READER));
        verifyPermissions(USER_READER, expectedDatasetRoles);

        log(String.format("Move the editor folder group '%s' to have no (NONE) permissions.", GROUP_EDITORS));
        adjustGroupDatasetPerms(GROUP_EDITORS, GroupSecuritySetting.NONE, null);

        log(String.format("Impersonate user '%s' and validate that they have no permissions.", USER_EDITOR));
        verifyNoDatasetPermissions(USER_EDITOR);

    }

    /**
     * Test the group permissions that are not per dataset specific.
     */
    @Test
    public void testBulkPermissionsGroup()
    {
        // Only going to check a few of the datasets per user/group.

        Map<String, DatasetRoles> expectedDatasetRoles = Map.of(DS_ALT_ID, DatasetRoles.EDITOR,
                                                                DS_DEMO, DatasetRoles.EDITOR);

        log("Verify system admins (current user) should be able to edit everything.");
        verifyPermissions(USER_ADMIN, expectedDatasetRoles);

        log(String.format("Group '%s' should be able to see all datasets, but not edit anything.", GROUP_READERS));
        expectedDatasetRoles = Map.of(DS_COHORT, DatasetRoles.READER,
                DS_ENROLL, DatasetRoles.READER);
        verifyPermissions(USER_READER, expectedDatasetRoles);

        log(String.format("Group '%s' should be able to see all datasets and edit them.", GROUP_EDITORS));
        expectedDatasetRoles = Map.of(DS_FOLLOW_UP, DatasetRoles.EDITOR,
                DS_MISSED, DatasetRoles.EDITOR);
        verifyPermissions(USER_EDITOR, expectedDatasetRoles);

        log(String.format("No datasets should be visible to group '%s'.", GROUP_NONE));
        verifyNoDatasetPermissions(USER_NONE);

    }

    /**
     * Simple test of groups with Per Dataset Permissions set.
     */
    @Test
    public void testSimplePerDatasetPermissions()
    {
        log(String.format("Verify the per dataset permissions for user '%s'. This verifies all per dataset permissions.", USER_LIMITED));
        Map<String, DatasetRoles> expectedDatasetRoles = Map.of(DS_ALT_ID, DatasetRoles.READER,
                DS_COHORT, DatasetRoles.READER,
                DS_DEMO, DatasetRoles.EDITOR,
                DS_ENROLL, DatasetRoles.NONE,
                DS_TYPES, DatasetRoles.AUTHOR);
        verifyPermissions(USER_LIMITED, expectedDatasetRoles);

        log(String.format("Verify the per dataset permissions for user '%s'.", USER_AUTHOR));
        expectedDatasetRoles = Map.of(DS_DEMO, DatasetRoles.AUTHOR,
                DS_ENROLL, DatasetRoles.AUTHOR,
                DS_MISSED, DatasetRoles.AUTHOR);
        verifyPermissions(USER_AUTHOR, expectedDatasetRoles);

        // Check for Issue 42681 (The principal Guests may not be assigned the role Restricted Reader! error in study security).
        // This will set the guest to a per dataset permission and then checks that the guest column is present and has
        // the expected values in the permissions dropdown.
        log(String.format("Verify that '%s' can have per dataset permissions.", GROUP_GUESTS));
        goToStudyFolder();
        StudySecurityPage studySecurityPage = _studyHelper.enterStudySecurity();
        studySecurityPage.setGroupStudySecurityAndUpdate(Map.of(GROUP_GUESTS, GroupSecuritySetting.PER_DATASET));

        List<DatasetRoles> expectedPermissions = Arrays.asList(DatasetRoles.NONE, DatasetRoles.READER, DatasetRoles.AUTHOR, DatasetRoles.EDITOR);
        List<DatasetRoles> actualPermissions = studySecurityPage.getAllowedDatasetPermissions(GROUP_GUESTS);

        Collections.sort(expectedPermissions);
        Collections.sort(actualPermissions);
        checker().verifyEquals(String.format("The 'Per Dataset Permissions' options not as expected for '%s'.", GROUP_GUESTS),
                expectedPermissions, actualPermissions);

    }

    /**
     * Validate that removing permissions and canceling changes work as expected.
     */
    @Test
    public void testPermissionsReset()
    {

        log(String.format("Change %s permissions to 'Read All' and verify.", GROUP_LIMITED));
        adjustGroupDatasetPerms(GROUP_LIMITED, GroupSecuritySetting.READ_ALL);
        Map<String, DatasetRoles> expectedDatasetRoles = Map.of(DS_DEMO, DatasetRoles.READER,
                DS_TYPES, DatasetRoles.READER);
        verifyPermissions(USER_LIMITED, expectedDatasetRoles);

        log("Check that the groups is no longer visible in the 'Per Dataset' panel.");

        StudySecurityPage studySecurityPage = _studyHelper.enterStudySecurity();
        List<String> groups = studySecurityPage.getGroupsListedInPerDatasets();
        checker().verifyFalse(String.format("Group '%s' still has a column in the 'Per Dataset' panel.", GROUP_LIMITED),
                groups.contains(GROUP_LIMITED));

        log(String.format("Reinstate per dataset permission to %s and dataset permissions default to 'NONE'.", GROUP_LIMITED));
        adjustGroupDatasetPerms(GROUP_LIMITED, GroupSecuritySetting.PER_DATASET);
        expectedDatasetRoles = Map.of(DS_DEMO, DatasetRoles.NONE,
                DS_ALT_ID, DatasetRoles.NONE,
                DS_TYPES, DatasetRoles.NONE,
                DS_COHORT, DatasetRoles.NONE);
        verifyPermissions(USER_LIMITED, expectedDatasetRoles);

        log("Set the permissions to what they were at the start.");
        expectedDatasetRoles = Map.of(DS_DEMO, DatasetRoles.EDITOR,
                DS_ALT_ID, DatasetRoles.READER,
                DS_TYPES, DatasetRoles.AUTHOR,
                DS_COHORT, DatasetRoles.READER);
        adjustGroupDatasetPerms(GROUP_LIMITED, GroupSecuritySetting.PER_DATASET, expectedDatasetRoles);

        log("Set them to something else and then cancel.");
        studySecurityPage = _studyHelper.enterStudySecurity();
        studySecurityPage.setDatasetPermissions(GROUP_LIMITED, DS_DEMO, DatasetRoles.NONE);
        studySecurityPage.setDatasetPermissions(GROUP_LIMITED, DS_ALT_ID, DatasetRoles.NONE);
        studySecurityPage.setDatasetPermissions(GROUP_LIMITED, DS_TYPES, DatasetRoles.NONE);
        studySecurityPage.setDatasetPermissions(GROUP_LIMITED, DS_COHORT, DatasetRoles.NONE);
        studySecurityPage.cancel();

        log("Go back and verify that the permissions are as expected after cancel.");
        goToStudyFolder();
        studySecurityPage = _studyHelper.enterStudySecurity();
        checker().verifyEquals(String.format("For group '%s' dataset '%s' should have permission '%s'.",
                    GROUP_LIMITED, DS_DEMO, DatasetRoles.EDITOR),
                DatasetRoles.EDITOR, studySecurityPage.getDatasetPermission(GROUP_LIMITED, DS_DEMO));

        verifyPermissions(USER_LIMITED, expectedDatasetRoles);
    }

    /**
     * Validate that a user can actually insert a record. The user is in a group that has editor permissions on the
     * 'types' dataset and read permissions on the alt-id dataset. These two permissions are needed to insert into
     * the types dataset.
     */
    @Test
    public void testUserCanActuallyInsert()
    {

        log("Validate that a user with a per dataset permission can really insert a record in that dataset.");
        goToStudyFolder();

        log(String.format("User '%s' has editor permissions for dataset '%s', go there and insert a record.",
                USER_LIMITED, DS_TYPES));
        WebElement datasetLink = Locator.linkWithText(DS_TYPES).findWhenNeeded(getDriver());
        clickAndWait(datasetLink);

        DataRegionTable dataRegionTable = new DataRegionTable("Dataset", getDriver());
        dataRegionTable.clickInsertNewRow();
        setFormElement(Locator.tagWithName("input", "quf_MouseId"), "12345");
        setFormElement(Locator.tagWithName("input", "quf_SequenceNum"), "1.1234");
        clickAndWait(Locator.lkButton("Submit"));

        WebElement header = Locator.tagWithClassContaining("div", "lk-body-title")
                .child(Locator.xpath(String.format("h3[contains(text(), '%s')]", DS_TYPES)))
                .findWhenNeeded(getDriver());

        checker()
                .withScreenshot(String.format("InsertToDatasetFailed_%s", DS_TYPES))
                .verifyTrue(
                        String.format("Successful insert should have sent us back to '%s' dataset.", DS_TYPES),
                        header.isDisplayed());
    }

    /**
     * Put a user into two groups, where each group has a different permissions setting for various datasets, then validate
     * that the more permissive role is the one that is taken.
     */
    @Test
    public void testUserInMultipleGroups()
    {
        log(String.format("Put user '%s' into groups '%s' and '%s'.", USER_IN_TWO, GROUP_AUTHORS, GROUP_LIMITED));

        ApiPermissionsHelper apiPermissionsHelper = new ApiPermissionsHelper(this);
        apiPermissionsHelper.addUserToProjGroup(USER_IN_TWO, getProjectName(), GROUP_AUTHORS);
        apiPermissionsHelper.addUserToProjGroup(USER_IN_TWO, getProjectName(), GROUP_LIMITED);

        log("Validate that the user has permission from both groups and that the more permissive role is used when there is a conflict.");
        Map<String, DatasetRoles> expectedDatasetRoles = Map.of(DS_ALT_ID, DatasetRoles.READER,
                DS_COHORT, DatasetRoles.READER,
                DS_DEMO, DatasetRoles.EDITOR,
                DS_TYPES, DatasetRoles.AUTHOR,
                DS_ENROLL, DatasetRoles.AUTHOR,
                DS_MISSED, DatasetRoles.AUTHOR);
        verifyPermissions(USER_IN_TWO, expectedDatasetRoles);
    }

    /**
     * Validate that the dirty page functionality works.
     *
     * I have no idea as to why, but the Firefox browser launched by the test automation is not showing an alert. It
     * appears that the alert shown from the security page is different from other alerts and this is a problem for
     * the automation.
     *
     * The functionality works fine manually.
     */
    //Issue 42762: Some browser dialogs are not shown when running test automation.
    @Ignore
    @Test
    public void testDirtyPage()
    {
        StudySecurityPage studySecurityPage = goToStudySecurityPage();

        studySecurityPage.setSecurityType(StudySecurityPage.StudySecurityType.BASIC_READ);
        clickTab("Overview", false);

        checkForDirtyPageAlert("Study Security Type");

        studySecurityPage = goToStudySecurityPage();

        studySecurityPage.setGroupStudySecurity(GROUP_LIMITED, GroupSecuritySetting.READ_ALL);
        clickTab("Overview", false);

        checkForDirtyPageAlert("Group Permissions");

        studySecurityPage = goToStudySecurityPage();

        studySecurityPage.setDatasetPermissions(GROUP_LIMITED, DS_TYPES, DatasetRoles.NONE);
        clickTab("Overview", false);

        checkForDirtyPageAlert("Group Permissions");

    }

    private void checkForDirtyPageAlert(String changeMade)
    {
        // I don't know what the deal is but I am not able to get the text for the alert (it always returns empty string).
        // So just checking if the alert is present.
        Alert alert = getAlertIfPresent();
        if(alert != null)
        {
            log(String.format("Alert message: '%s'.", alert.getText()));
            alert.accept();
        }
        else
        {
            checker()
                    .withScreenshot("testDirtyPage_GroupPermissions")
                    .error(String.format("No alert shown after changing the '%s' and navigating away.", changeMade));

        }

    }

    protected void adjustGroupDatasetPerms(String groupName, GroupSecuritySetting setting)
    {
        adjustGroupDatasetPerms(groupName, setting, null);
    }

    protected void adjustGroupDatasetPerms(String groupName, GroupSecuritySetting setting, Map<String, DatasetRoles> datasetRolesMap)
    {
        goToStudyFolder();
        StudySecurityPage studySecurityPage = _studyHelper.enterStudySecurity();

        studySecurityPage.setGroupStudySecurity(groupName, setting);
        studySecurityPage.updateGroupSecurity();

        if (null != datasetRolesMap && !datasetRolesMap.isEmpty())
        {
            studySecurityPage.setDatasetPermissionsAndSave(groupName, datasetRolesMap);
            studySecurityPage.saveDatasetPermissions();
        }

        clickFolder(FOLDER_NAME);
    }

    // Special case the check when a user should have no permissions to any datasets.
    private void verifyNoDatasetPermissions(String user)
    {

        goToStudyFolder();

        checker()
                .verifyNotEquals(
                        String.format("You are checking to see if site admin '%s' has no permissions, that is just plain wrong.", user),
                        USER_ADMIN, user);

        impersonate(user);

        WebElement datasetsPanel = Locator
                .tagWithClassContaining("div", "panel-body")
                .findElements(getDriver()).get(2);
        List<WebElement> datasetLinks = Locator.tag("table").childTag("a").findElements(datasetsPanel);

        checker()
                .withScreenshot("Dataset_Links_Present")
                .verifyTrue(String.format("There should be no links visible to user '%s'.", user),
                        datasetLinks.isEmpty());

        stopImpersonating();

    }

    private void verifyPermissions(String user, Map<String, DatasetRoles> expectedDatasetRoles)
    {

        goToStudyFolder();

        if(!user.equalsIgnoreCase(USER_ADMIN))
            impersonate(user);

        for (Map.Entry<String, DatasetRoles> entry : expectedDatasetRoles.entrySet())
        {

            String datasetName = entry.getKey();

            WebElement datasetLink = Locator.linkWithText(datasetName).findWhenNeeded(getDriver());

            if (entry.getValue().equals(DatasetRoles.NONE))
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

        if(!user.equalsIgnoreCase(USER_ADMIN))
            stopImpersonating();

        // Go back to where you started.
        goToStudyFolder();

    }

    private void verifyDataRegionTable(String datasetName, DatasetRoles datasetRole)
    {
        DataRegionTable dataRegionTable = new DataRegionTable("Dataset", getDriver());
        List<WebElement> updateLinks = DataRegionTable.updateLinkLocator().findElements(getDriver());

        if(datasetRole.equals(DatasetRoles.READER))
        {
            log("Validate the 'reader' role does not have permission to update or insert.");
            checker()
                    .verifyFalse(
                            String.format("The user should only have read permissions to dataset '%s' but the 'insert button' is present.",
                                    datasetName),
                            dataRegionTable.hasHeaderMenu(INSERT_BUTTON_TEXT));

            checker()
                    .verifyEquals(
                            String.format("User should not be able to edit rows in '%s' but update link(s) is/are present.",
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
                log("For the 'author' roles none of the entries in the dataset should be editable.");
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
                GROUP_AUTHORS, GroupSecuritySetting.PER_DATASET,
                GROUP_LIMITED, GroupSecuritySetting.PER_DATASET,
                GROUP_NONE, GroupSecuritySetting.NONE,
                GROUP_DEVELOPER, GroupSecuritySetting.NONE,
                GROUP_GUESTS, GroupSecuritySetting.NONE,
                GROUP_ALL_USERS, GroupSecuritySetting.NONE,
                GROUP_USERS, GroupSecuritySetting.NONE);

        studySecurityPage.setGroupStudySecurityAndUpdate(groupSettings);

        // Clear any previous per dataset permissions.
        studySecurityPage.clearAll();

        // Grant rights to the LIMITED group for a couple of datasets.
        Map<String, DatasetRoles> permissions = Map.of(DS_DEMO, DatasetRoles.EDITOR,
                                                DS_ALT_ID, DatasetRoles.READER,
                                                DS_TYPES, DatasetRoles.AUTHOR,
                                                DS_COHORT, DatasetRoles.READER);

        studySecurityPage.setDatasetPermissionsAndSave(GROUP_LIMITED, permissions);

        // Grant author rights to the AUTHORS group for a couple of datasets.
        permissions = Map.of(DS_DEMO, DatasetRoles.AUTHOR,
                DS_ENROLL, DatasetRoles.AUTHOR,
                DS_MISSED, DatasetRoles.AUTHOR);

        studySecurityPage.setDatasetPermissionsAndSave(GROUP_AUTHORS, permissions);

    }

    private StudySecurityPage goToStudySecurityPage()
    {
        StudySecurityPage studySecurityPage;
        if(!getCurrentRelativeURL().toLowerCase().contains("study-security-begin.view"))
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

    private void goToStudyFolder()
    {
        String currentUrl = getCurrentRelativeURL().toLowerCase();

        if(!currentUrl.contains(FOLDER_NAME.toLowerCase()))
        {
            navigateToFolder(getProjectName(), FOLDER_NAME);
        }

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
