/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.pages.study.ManageVisitPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.ListHelper;
import org.labkey.test.util.StudyHelper;

import java.io.File;

public abstract class StudyManualTest extends StudyTest
{
    private final File CRF_SCHEMAS = TestFileUtils.getSampleData("study/datasets/schema.tsv");
    protected final File VISIT_MAP = TestFileUtils.getSampleData("study/v068_visit_map.xml");

    protected final StudyHelper _studyHelper = new StudyHelper(this);

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    protected void doCreateSteps()
    {
        createStudyManually();
        startSpecimenImport(2);

        afterManualCreate();
    }

    protected void createStudyManually()
    {
        triggerManualTest();
        initializeFolder();

        clickButton("Create Study");
        click(Locator.radioButtonByNameAndValue("simpleRepository", "false"));
        setFormElement(Locator.name("subjectNounSingular"), "Mouse");
        setFormElement(Locator.name("subjectNounPlural"), "Mice");
        setFormElement(Locator.name("subjectColumnName"), "MouseId");
        clickButton("Create Study");

        // change study label
        clickAndWait(Locator.linkWithText("Change Study Properties"));
        waitForElement(Locator.name("Label"), WAIT_FOR_JAVASCRIPT);
        setFormElement(Locator.name("Label"), getStudyLabel());
        clickButton("Submit");
        clickTab("Overview");
        assertTextPresent(getStudyLabel());

        // import visit map
        _studyHelper.goToManageVisits().goToImportVisitMap();
        setFormElement(Locator.name("content"), TestFileUtils.getFileContents(VISIT_MAP));
        clickButton("Import");

        // import custom visit mapping
        importCustomVisitMappingAndVerify();

        // define forms
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        clickAndWait(Locator.linkWithText("Define Dataset Schemas"));
        clickAndWait(Locator.linkWithText("Bulk Import Schemas"));
        setFormElement(Locator.name("typeNameColumn"), "platename");
        setFormElement(Locator.name("labelColumn"), "platelabel");
        setFormElement(Locator.name("typeIdColumn"), "plateno");
        setFormElement(Locator.name("tsv"), TestFileUtils.getFileContents(CRF_SCHEMAS));
        clickButton("Submit", 180000);

        // setup cohorts:
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Cohorts"));
        selectOptionByText(Locator.name("participantCohortDatasetId"), "EVC-1: Enrollment Vaccination");
        selectOptionByText(Locator.name("participantCohortProperty"), "2. Enrollment group");
        clickButton("Update Assignments");

        // configure QC state management so that all data is displayed by default (we'll test with hidden data later):
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Dataset QC States"));
        selectOptionByText(Locator.name("showPrivateDataByDefault"), "All data");
        clickButton("Save");

        // upload datasets:
        setPipelineRoot(StudyHelper.getPipelinePath());
        clickTab("Overview");
        clickAndWait(Locator.linkWithText("Manage Files"));
        clickButton("Process and Import Data");
        _fileBrowserHelper.selectFileBrowserItem("datasets/Study001.dataset");
        if (isButtonPresent("Delete log"))
            clickButton("Delete log");
        _fileBrowserHelper.selectImportDataAction("Import Datasets");
        clickButton("Start Import");
        waitForPipelineJobsToComplete(1, "study import", false);
    }


    // Using old visit map format, which does not support default visibility, etc. (so we need to set these manually).
    // TODO: We're no longer using the old visit map format... move these settings into visit_map.xml?
    protected void afterManualCreate()
    {
        hideSceeningVisit();
        setDemographicsDescription();
        setDemographicsBit();
        createCustomAssays();
    }


    protected void hideSceeningVisit()
    {
        clickFolder(getFolderName());
        hideVisits("Screening Cycle", "Cycle 1");
    }


    protected void importCustomVisitMappingAndVerify()
    {
        // Import custom mapping
        importCustomVisitMapping();

        // Test clearing the custom mapping
        clickAndWait(Locator.linkWithText("Clear Custom Mapping"));
        clickAndWait(Locator.linkWithText("OK"));
        assertTextPresent("The custom mapping is currently empty");
        assertElementPresent(Locator.lkButton("Import Custom Mapping"));
        assertElementNotPresent(Locator.lkButton("Replace Custom Mapping"));
        assertElementNotPresent(Locator.lkButton("Clear Custom Mapping"));
        assertTextNotPresent("Vaccine 1", "Vaccination 1", "Cycle 10", "All Done");

        // Import custom mapping again
        importCustomVisitMapping();
    }


    protected void importCustomVisitMapping()
    {
        if (!isElementPresent(Locator.linkContainingText("Visit Import Mapping")))
        {
            clickTab("Manage");
            clickAndWait(Locator.linkWithText("Manage Visits"));
        }

        clickAndWait(Locator.linkWithText("Visit Import Mapping"));
        clickButton("Import Custom Mapping");
        setFormElement(Locator.id("tsv"), VISIT_IMPORT_MAPPING);
        clickButton("Submit");

        assertTextPresentInThisOrder("Cycle 10", "Vaccine 1", "Vaccination 1", "All Done");
    }


    protected void setDemographicsDescription()
    {
        clickFolder(getFolderName());
        _studyHelper.goToManageDatasets()
                .selectDatasetByName("DEM-1")
                .clickEditDefinition()
                .setDescription(DEMOGRAPHICS_DESCRIPTION)
                .save();
    }


    protected void setDemographicsBit()
    {
        clickFolder(getFolderName());
        setDemographicsBit("DEM-1: Demographics", true);
    }


    // Hide visits based on label -- manual create vs. import will result in different indexes for these visits
    protected void hideVisits(String... visitLabels)
    {
        _studyHelper.goToManageVisits();

        for (String visitLabel : visitLabels)
        {
            new ManageVisitPage(getDriver()).goToEditVisit(visitLabel);
            uncheckCheckbox(Locator.name("showByDefault"));
            clickButton("Save");
        }
    }

    protected void createCustomAssays()
    {
        clickFolder(getFolderName());
        EditDatasetDefinitionPage editDatasetPage = _studyHelper.goToManageDatasets()
                .clickCreateNewDataset()
                .setName("verifyAssay")
                .submit();

        waitForElement(Locator.xpath("//input[@id='DatasetDesignerName']"), WAIT_FOR_JAVASCRIPT);

        checkRadioButton(Locator.radioButtonByName("additionalKey").index(1));

        clickButton("Import Fields", 0);
        waitForElement(Locator.xpath("//textarea[@id='schemaImportBox']"), WAIT_FOR_JAVASCRIPT);

        setFormElement(Locator.id("schemaImportBox"), "Property\tLabel\tRangeURI\tNotNull\tDescription\n" +
                "SampleId\tSample Id\txsd:string\ttrue\tstring\n" +
                "DateField\tDateField\txsd:dateTime\tfalse\tThis is a date field\n" +
                "NumberField\tNumberField\txsd:double\ttrue\tThis is a number\n" +
                "TextField\tTextField\txsd:string\tfalse\tThis is a text field");

        clickButton("Import", 0);
        waitForElement(Locator.xpath("//input[@name='ff_label3']"), WAIT_FOR_JAVASCRIPT);

        click(Locator.radioButtonById("button_dataField"));

        _listHelper.addField("Dataset Fields", "otherData", "Other Data", ListHelper.ListColumnType.String);
        click(Locator.xpath("//span[contains(@class,'x-tab-strip-text') and text()='Advanced']"));
        waitForElement(Locator.id("importAliases"), WAIT_FOR_JAVASCRIPT);
        setFormElement(Locator.id("importAliases"), "aliasedColumn");

        editDatasetPage
                .save()
                .clickViewData()
                .getDataRegion()
                .clickImportBulkData();

        String errorRow = "\tbadvisitd\t1/1/2006\t\ttext\t";
        setFormElement(Locator.name("text"), _tsv + "\n" + errorRow);
        _listHelper.submitImportTsv_error("Could not convert 'badvisitd'");
        assertTextPresent("Could not convert 'text'");

        _listHelper.submitTsvData(_tsv);
        assertTextPresent("1234", "2006-02-01", "1.2", "aliasedData");
    }
}
