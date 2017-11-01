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
import org.labkey.test.categories.DailyC;
import org.labkey.test.pages.DatasetPropertiesPage;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.DataRegionTable;

@Category(DailyC.class)
public class StudyDatasetImportFieldsTest extends StudyBaseTest
{
    private static final String PROJECT_NAME =  "Dataset Import Fields Test Project";
    private static final String STUDY_NAME = "Dataset Import Fields Test Study";
    private static final String FOLDER_NAME =  "Dataset Import Fields Test Folder";
    private static final String INITIAL_COL = "Test Field";
    private static final String INITIAL_COL_VAL = "Some data";
    private static final String REPLACEMENT_COL = "Nukeum";

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
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected void doCreateSteps()
    {
        initializeFolder();
        clickFolder(getFolderName());
    }

    @Override
    protected void doVerifySteps() throws Exception
    {
        clickButton("Create Study");
        waitForText(FOLDER_NAME + " Study");
        clickButton("Create Study");
        EditDatasetDefinitionPage editDatasetPage = _studyHelper.goToManageDatasets()
                .clickCreateNewDataset()
                .setName("Test Dataset")
                .submit();
        waitForElement(Locator.name("ff_name0"));
        setFormElement(Locator.name("ff_name0"), INITIAL_COL);
        editDatasetPage
                .save()
                .clickViewData();
        DataRegionTable.DataRegion(getDriver()).find().clickInsertNewRow();
        waitForElement(Locator.name("quf_ParticipantId"));
        setFormElement(Locator.name("quf_ParticipantId"), "47");
        setFormElement(Locator.name("quf_SequenceNum"), "47");
        setFormElement(Locator.name("quf_date"), "4/25/2014");
        setFormElement(Locator.name("quf_Test Field"), INITIAL_COL_VAL);
        clickButton("Submit");
        waitForText("Participant ID");
        assertTextPresent(INITIAL_COL_VAL);
        _extHelper.clickMenuButton(true, "Manage");
        editDatasetPage = new DatasetPropertiesPage(getDriver())
                .clickEditDefinition();
        waitForText("Import Fields");
        clickButton("Import Fields", "WARNING");
        setFormElement(Locator.id("schemaImportBox"), "Property\n" + REPLACEMENT_COL);
        clickButton("Import", 0);
        waitForText(REPLACEMENT_COL);
        editDatasetPage
                .save()
                .clickViewData();
        //waitForText("47");
        waitForText("No data to show.");
        assertTextPresent(REPLACEMENT_COL);

        /* Formerly something in this test produced 1 expected error. This changed between r33104 and r33123, and there are now no errors
            produced by the test steps. Unfortunately the original developer (myself) wasn't nice enough to say in comments
            what the expected error was. None of the steps look like they were intentionally triggering an error via a negative test
            case.
        */
        checkExpectedErrors(0);
    }
}
