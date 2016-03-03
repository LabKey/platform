/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
import org.labkey.test.categories.DailyB;
import org.labkey.test.tests.StudyBaseTest;

/**
 * User: tgaluhn
 * Date: 4/25/2014
 */
@Category(DailyB.class)
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
        clickButton("Create Study", 0);
        waitForText(FOLDER_NAME + " Study");
        clickButton("Create Study", 0);
        waitForText("Manage Datasets");
        _studyHelper.goToManageDatasets();
        click(Locator.linkWithText("Create New Dataset"));
        waitForElement(Locator.name("typeName"));
        setFormElement(Locator.name("typeName"), "Test Dataset");
        clickButton("Next", 0);
        waitForElement(Locator.name("ff_name0"));
        setFormElement(Locator.name("ff_name0"), INITIAL_COL);
        clickButton("Save", 0);
        waitForElement(Locator.linkWithText("View Data"));
        click(Locator.linkWithText("View Data"));
        waitForText("Insert New");
        clickAndWait(Locator.linkWithText("Insert New"));
        waitForElement(Locator.name("quf_ParticipantId"));
        setFormElement(Locator.name("quf_ParticipantId"), "47");
        setFormElement(Locator.name("quf_SequenceNum"), "47");
        setFormElement(Locator.name("quf_date"), "4/25/2014");
        setFormElement(Locator.name("quf_Test Field"), INITIAL_COL_VAL);
        clickButton("Submit", 0);
        waitForText("Manage Dataset");
        assertTextPresent(INITIAL_COL_VAL);
        click(Locator.linkWithText("Manage Dataset"));
        waitForText("Edit Definition");
        click(Locator.linkWithText("Edit Definition"));
        waitForText("Import Fields");
        clickButton("Import Fields", "WARNING");
        setFormElement(Locator.id("schemaImportBox"), "Property\n" + REPLACEMENT_COL);
        clickButton("Import", 0);
        waitForText(REPLACEMENT_COL);
        clickButton("Save", 0);
        waitForText("View Data");
        click(Locator.linkWithText("View Data"));
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
