/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyC;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.Ext4Helper;

import java.io.File;

@Category(DailyC.class)
public class StudyMergeParticipantsTest extends StudyBaseTest
{
    private static final String PROJECT_NAME =  "Merge Participants Test Project";
    private static final String STUDY_NAME = "Merge Participants Test Study";
    private static final String FOLDER_NAME =  "Merge Participants Test Folder";
    private static final File STUDY_ZIP = TestFileUtils.getSampleData("studies/LabkeyDemoStudy.zip");
    private static final String SUBJECT_NOUN = "Participant";
    private static final String SUBJECT_NOUN_PLURAL = "Participants";
    private static final String SUBJECT_COLUMN = SUBJECT_NOUN + "Id";
    private static final String ALIAS_DATASET = "MyAliasDataset";
    private static final String ALIAS_COLUMN = "MyAlias";
    private static final String SOURCE_COLUMN = "MySource";

    private static final String PTID_WITH_ALIAS = "249318596";
    private static final String PTID_NO_ALIAS = "249320107";
    private static final String PTID_NEW_1 = "xyz987";
    private static final String PTID_NEW_2 = "249325717";

    private static final String ALIAS_SOURCE_1 = "a";
    private static final String ALIAS_SOURCE_2 = "b";
    private static final Locator.XPathLocator CREATE_ALIAS_CB = Ext4Helper.Locators.ext4CheckboxById("createAliasCB");
    private static final Locator.NameLocator OLD_ID_FIELD = Locator.name("oldIdField-inputEl");
    private static final Locator.NameLocator NEW_ID_FIELD = Locator.name("newIdField-inputEl");
    private static final Locator.NameLocator ALIAS_SOURCE_FIELD = Locator.name("aliasSourceField-inputEl");

    private static final int MERGE_SUCCESS_TIMEOUT = 60000;


    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Override
    protected String getStudyLabel()
    {
        return STUDY_NAME;
    }

    @Override
    protected String getFolderName()
    {
        return FOLDER_NAME;
    }

    @Override
    protected void doCreateSteps()
    {
        initializeFolder();
        clickFolder(getFolderName());
        log("Import LabkeyDemoStudy");
        importStudyFromZip(STUDY_ZIP);
    }

    @Override
    protected void doVerifySteps()
    {
        goToMergeParticipants();
        log("Ensure alias creation controls absent when no alias dataset configured");
        assertElementNotVisible(CREATE_ALIAS_CB);

        configureAliases();
        goToMergeParticipants();

        log("Check have error for alias creation, as source field is blank");
        assertElementPresent(ALIAS_SOURCE_FIELD); // if it's missing, the participant alias setup didn't work.
        setFormElement(OLD_ID_FIELD, PTID_WITH_ALIAS);
        setFormElement(NEW_ID_FIELD, PTID_NEW_1);
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Preview"));
        waitForElement(Locator.tag("span").containing("Specimen data is not editable"), MERGE_SUCCESS_TIMEOUT);
        // Error on missing value for source field.
        assertElementPresent(Locator.tag("span").containing("Missing value for required property"));

        log("Check not reporting conflict when no conflict exists, and warning on existing alias");
        setFormElement(ALIAS_SOURCE_FIELD, ALIAS_SOURCE_2);
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Preview"));
        waitForElement(Locator.tag("span").containing("Preview Complete"), MERGE_SUCCESS_TIMEOUT);
        assertElementPresent(Locator.tag("td").containing("Warning: Specimen data is not editable"));
        assertElementNotPresent(Locator.linkContainingText("Conflict!"));
        assertElementPresent(Locator.tag("td").containing("Aliases are not updated by this process"));
        assertElementPresent(Locator.tag("td").containing("Warning: " + PTID_WITH_ALIAS + " has existing aliases"));

        log("Check merge successful and alias created.");
        clickButton("Merge", 0);
        waitForElement(Locator.tag("span").containing("Successfully merged"), MERGE_SUCCESS_TIMEOUT);
        clickTab(SUBJECT_NOUN_PLURAL);
        waitForElement(Locator.linkContainingText(PTID_NEW_1)); // New participantId now exists
        assertElementNotPresent(Locator.linkContainingText(PTID_WITH_ALIAS));
        click(Locator.linkContainingText(PTID_NEW_1));
        waitForElement(Locator.tag("p").containing("b: " + PTID_WITH_ALIAS));

        log("Check conflicts are correctly detected and prevent processing.");
        goToMergeParticipants();
        click(CREATE_ALIAS_CB);
        setFormElement(OLD_ID_FIELD, PTID_NO_ALIAS);
        setFormElement(NEW_ID_FIELD, PTID_NEW_2);
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Preview"));
        waitForElement(Locator.tag("span").containing("Preview Complete"), MERGE_SUCCESS_TIMEOUT);
        assertElementNotPresent(Locator.linkContainingText(PTID_NO_ALIAS + " has existing aliases"));
        assertElementPresent(Locator.linkContainingText("Conflict!"));
        clickButton("Merge", 0);
        waitForElement(Locator.tag("span").containing("You must choose"));

        log("Check url's to are correctly constructed");
        final String url = WebTestHelper.buildRelativeUrl("study", PROJECT_NAME + "/" + FOLDER_NAME, "dataset") + "?datasetId=5018&Dataset.ParticipantId~in=" + PTID_NO_ALIAS + "%3B" + PTID_NEW_2;
        assertElementPresent(Locator.linkWithHref(url));

        log("Resolve conflicts and check for correct row retention");
        click(Locator.radioButtonByNameAndValue("conflict_Demographics", "new"));
        click(Locator.radioButtonByNameAndValue("conflict_Participation and Genetic Consent", "old"));

        clickButton("Merge", 0);
        waitForElement(Locator.tag("span").containing("Successfully merged"), MERGE_SUCCESS_TIMEOUT);
        clickTab(SUBJECT_NOUN_PLURAL);
        waitAndClick(Locator.linkWithText(PTID_NEW_2));
        waitForElement(Locator.tag("th").containing("5008: Demographics"));
        assertElementPresent(Locator.tag("td").containing("Start Date").append("/following-sibling::td['2008-04-27']")); //value from oldId demographic row
        assertElementPresent(Locator.tag("td").containing("Genetic Consent").append("/following-sibling::td['false']")); //value from newId consent row
    }

    private void configureAliases()
    {

        // Create alias dataset and insert some data
        EditDatasetDefinitionPage editDatasetPage = _studyHelper.goToManageDatasets()
                .clickCreateNewDataset()
                .setName(ALIAS_DATASET)
                .submit();
        clickButton("Import Fields", "Paste tab-delimited");
        setFormElement(Locator.name("tsv"), "Property\tNotNull\n" + ALIAS_COLUMN + "\tTRUE\n" + SOURCE_COLUMN + "\tTRUE");
        clickButton("Import", ALIAS_COLUMN);
        click(Locator.radioButtonById("button_dataField"));
        selectOptionByValue(Locator.id("list_dataField"), SOURCE_COLUMN);
        editDatasetPage
                .save()
                .clickViewData()
                .getDataRegion()
                .clickImportBulkData();
        setFormElement(Locator.name("text"), "participantId\tdate\t" + ALIAS_COLUMN + "\t" + SOURCE_COLUMN + "\n" + PTID_WITH_ALIAS + "\t1/3/2014\tabc123\t" + ALIAS_SOURCE_1);
        clickButton("Submit", "Dataset: " + ALIAS_DATASET + ", All Visits");

        // Configure new dataset as the alias dataset
        goToManageAlternateIds();
        _ext4Helper.selectComboBoxItem("Dataset Containing Aliases", ALIAS_DATASET);
        _ext4Helper.selectComboBoxItem("Alias Column", ALIAS_COLUMN);
        _ext4Helper.selectComboBoxItem("Source Column", SOURCE_COLUMN);
        clickButton("Save Changes", 0);
        waitForText(SUBJECT_NOUN + " alias settings saved successfully");
        clickButton("OK", 0);

        // Refresh the page and make sure the configuration stuck
        refresh();
        waitForFormElementToEqual(Locator.input("datasetCombo"),ALIAS_DATASET);

        clickButton("Done", "Manage Study");
    }

    @Override
    public void runApiTests() throws Exception
    {
        // No API tests (yet... ever?)
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    private void goToManageAlternateIds()
    {
        goToManageStudy();
        clickAndWait(Locator.linkContainingText("Manage Alternate"));
    }

    private void goToMergeParticipants()
    {
        goToManageAlternateIds();
        clickButton("Change or Merge " + SUBJECT_COLUMN);
    }
}
