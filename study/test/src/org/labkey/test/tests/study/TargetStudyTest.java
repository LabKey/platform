/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.SortDirection;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.Assays;
import org.labkey.test.categories.DailyB;
import org.labkey.test.tests.AbstractAssayTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PortalHelper;
import org.openqa.selenium.By;

import java.io.File;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Category({DailyB.class, Assays.class})
public class TargetStudyTest extends AbstractAssayTest
{
    private static final String ASSAY_NAME = "Assay";
    private static final String STUDY1_LABEL = "AwesomeStudy1";
    private static final String STUDY2_LABEL = "AwesomeStudy2";
    private static final String STUDY3_LABEL = "AwesomeStudy3";

    protected static final String TEST_RUN1 = "FirstRun";
    protected static final String TEST_RUN1_DATA1 =
            "specimenID\tparticipantID\tvisitID\tTargetStudy\n" +
            // study 1: full container path
            "AAA07XK5-05\t\t\t" + ("/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY1) + "\n" +
            // study 1: container id
            "AAA07XMC-02\t\t\t${Study1ContainerID}\n" +
            // study 1: study label
            "AAA07XMC-04\t\t\t${Study1Label}" + "\n" +
            // fake study / no study
            "AAA07XSF-02\t\t\tStudyNotExist\n" +
            // study 2
            "AAA07YGN-01\t\t\t" +("/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY2) + "\n" +
            // study 3
            "AAA07YGN-02\t\t\t" +("/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY3) + "\n"
            ;

    private String _study1ContainerId = null;
    private String _study1Label = null;
    private String _study2Label = null;
    private String _study3Label = null;

    @Override
    protected String getProjectName()
    {
        return TEST_ASSAY_PRJ_SECURITY;
    }


    @Test
    public void runUITests() throws Exception
    {
        log("** Setup");
        setupEnvironment();
        setupSpecimens();
        setupLabels();
        setupAssay();

        clickProject(TEST_ASSAY_PRJ_SECURITY);
        _study1ContainerId = getContainerId("/project/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY1 + "/begin.view");
        log("** Study 1 container ID = " + _study1ContainerId);
        assertNotNull(_study1ContainerId);

        uploadRuns();
        copyToStudy();
    }


    @LogMethod
    protected void setupSpecimens() throws Exception
    {
        log("** Import specimens into Study 1 and Study 2");
        setupPipeline(TEST_ASSAY_PRJ_SECURITY);
        SpecimenImporter importer1 = new SpecimenImporter(TestFileUtils.getTestTempDir(), new File(TestFileUtils.getLabKeyRoot(), "/sampledata/study/specimens/sample_a.specimens"), new File(TestFileUtils.getTestTempDir(), "specimensSubDir"), TEST_ASSAY_FLDR_STUDY1, 1);
        importer1.startImport();

        SpecimenImporter importer2 = new SpecimenImporter(TestFileUtils.getTestTempDir(), new File(TestFileUtils.getLabKeyRoot(), "/sampledata/study/specimens/sample_a.specimens"), new File(TestFileUtils.getTestTempDir(), "specimensSubDir"), TEST_ASSAY_FLDR_STUDY2, 1);
        importer2.startImport();

        importer1.waitForComplete();
        importer2.waitForComplete();

    }

    @LogMethod
    protected void setupLabels()
    {
        // Using a random label helps uniqueify the study when there is another "AwesomeStudy3" from a previous test run.
        Random r = new Random();
        _study1Label = STUDY1_LABEL + " " + r.nextInt();
        _study2Label = STUDY2_LABEL + " " + r.nextInt();
        _study3Label = STUDY3_LABEL + " " + r.nextInt();

        log("** Set some awesome study labels");
        beginAt("/study/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY1 + "/manageStudyProperties.view");
        waitForElement(Locator.name("Label"), WAIT_FOR_JAVASCRIPT);
        setFormElement(Locator.name("Label"), _study1Label);
        clickButton("Submit");

        beginAt("/study/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY2 + "/manageStudyProperties.view");
        waitForElement(Locator.name("Label"), WAIT_FOR_JAVASCRIPT);
        setFormElement(Locator.name("Label"), _study2Label);
        clickButton("Submit", 0);
        // Save is via AJAX, but redirects to the general study settings page when it's done
        waitForText("General Study Settings");

        beginAt("/study/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY3 + "/manageStudyProperties.view");
        waitForElement(Locator.name("Label"), WAIT_FOR_JAVASCRIPT);
        setFormElement(Locator.name("Label"), _study3Label);
        clickButton("Submit", 0);
        // Save is via AJAX, but redirects to the general study settings page when it's done
        waitForText("General Study Settings");
    }

    @LogMethod
    protected void setupAssay()
    {
        PortalHelper portalHelper = new PortalHelper(this);

        clickProject(TEST_ASSAY_PRJ_SECURITY);
        if (!isElementPresent(Locator.linkWithText("Assay List")))
            portalHelper.addWebPart("Assay List");

        _assayHelper.uploadXarFileAsAssayDesign(TestFileUtils.getSampleData("TargetStudy/Assay.xar"), 1);
    }

    protected void uploadRuns()
    {
        log("** Upload Data");
        clickProject(TEST_ASSAY_PRJ_SECURITY);

        clickAndWait(Locator.linkWithText("Assay List"));
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        clickButton("Import Data");

        setFormElement(Locator.name("name"), TEST_RUN1);
        click(Locator.xpath("//input[@value='textAreaDataProvider']"));
        String data1 = TEST_RUN1_DATA1
                .replace("${Study1ContainerID}", _study1ContainerId)
                .replace("${Study1Label}", _study1Label);
        setFormElement(Locator.name("TextAreaDataCollector.textArea"), data1);
        clickButton("Save and Finish");
        assertTextPresent("Couldn't resolve TargetStudy 'StudyNotExist' to a study folder.");

        click(Locator.xpath("//input[@value='textAreaDataProvider']"));
        String data2 = data1.replace("StudyNotExist", "");
        setFormElement(Locator.name("TextAreaDataCollector.textArea"), data2);
        clickButton("Save and Finish");
        assertNoLabKeyErrors();

        log("** Test the TargetStudy renderer resolved all studies");
        clickAndWait(Locator.linkWithText(TEST_RUN1));
        // all target study values should render as either [None] or the name of the study
        assertTextNotPresent(
                "/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY1,
                "/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY2,
                "/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY3);

        DataRegionTable table = new DataRegionTable("Data", this);
        assertEquals(_study1Label, table.getDataAsText(0, "Target Study"));
        assertEquals(_study1Label, table.getDataAsText(1, "Target Study"));
        assertEquals(_study1Label, table.getDataAsText(2, "Target Study"));
        //BUGBUG: target study renders as "" instead of "[None]"
        //assertEquals("[None]", table.getDataAsText(3, "Target Study"));
        assertEquals(" ", table.getDataAsText(3, "Target Study"));
        assertEquals(_study2Label, table.getDataAsText(4, "Target Study"));
        assertEquals(_study3Label, table.getDataAsText(5, "Target Study"));

        log("** Check SpecimenID resolved the PTID in the study");
        assertEquals("999320812", table.getDataAsText(0, "Participant ID"));
        assertEquals("999320396", table.getDataAsText(1, "Participant ID"));
        assertEquals("999320396", table.getDataAsText(2, "Participant ID"));
        assertEquals(" ", table.getDataAsText(3, "Participant ID"));
        assertEquals("999320706", table.getDataAsText(4, "Participant ID"));
        assertEquals(" ", table.getDataAsText(5, "Participant ID"));
    }

    protected void copyToStudy()
    {
        DataRegionTable table = new DataRegionTable("Data", this);
        table.checkAllOnPage();
        clickButton("Copy to Study");

        log("** Check TargetStudy dropdowns");
        final String study1OptionText = "/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY1 + " (" + _study1Label + ")";
        final String study2OptionText = "/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY2 + " (" + _study2Label + ")";
        final String study3OptionText = "/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY3 + " (" + _study3Label + ")";
        assertEquals(study1OptionText, getSelectedOptionText(table.findCell(0, 0).findElement(By.xpath("select[@name='targetStudy']"))));
        assertEquals(study1OptionText, getSelectedOptionText(table.findCell(1, 0).findElement(By.xpath("select[@name='targetStudy']"))));
        assertEquals(study1OptionText, getSelectedOptionText(table.findCell(2, 0).findElement(By.xpath("select[@name='targetStudy']"))));
        assertEquals("[None]", getSelectedOptionText(table.findCell(3, 0).findElement(By.xpath("select[@name='targetStudy']"))));
        assertEquals(study2OptionText, getSelectedOptionText(table.findCell(4, 0).findElement(By.xpath("select[@name='targetStudy']"))));
        assertEquals(study3OptionText, getSelectedOptionText(table.findCell(5, 0).findElement(By.xpath("select[@name='targetStudy']"))));

        log("** Check ptid/visit matches for rows 0-2 and 4, no match for rows 3 and 5");
        assertAttributeContains(table.findCell(0, 1).findElement(By.xpath("i")), "class", "fa fa-check");
        assertAttributeContains(table.findCell(1, 1).findElement(By.xpath("i")), "class", "fa fa-check");
        assertAttributeContains(table.findCell(2, 1).findElement(By.xpath("i")), "class", "fa fa-check");
        assertAttributeContains(table.findCell(3, 1).findElement(By.xpath("i")), "class", "fa fa-times");
        assertAttributeContains(table.findCell(4, 1).findElement(By.xpath("i")), "class", "fa fa-check");
        assertAttributeContains(table.findCell(5, 1).findElement(By.xpath("i")), "class", "fa fa-times");

        clickButton("Re-Validate");
        assertTextPresent("You must specify a Target Study for all selected rows.");

        log("** Uncheck row 3 and 5");
        table.uncheckCheckbox(3);
        table.uncheckCheckbox(5);
        clickButton("Re-Validate");
        assertTextNotPresent("You must specify a Target Study for all selected rows.");

        log("** Copy to studies");
        clickButton("Copy to Study");

        beginAt("/study/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY1 + "/dataset.view?datasetId=5001");
        DataRegionTable dataset = new DataRegionTable("Dataset", this);
        assertEquals(3, dataset.getDataRowCount());
        dataset.setSort("ParticipantId", SortDirection.ASC);
        assertEquals(3, dataset.getDataRowCount());
        assertEquals("999320396", dataset.getDataAsText(0, "Participant ID"));
        assertEquals("999320396", dataset.getDataAsText(1, "Participant ID"));
        assertEquals("999320812", dataset.getDataAsText(2, "Participant ID"));

        beginAt("/study/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY2 + "/dataset.view?datasetId=5001");
        assertEquals(1, dataset.getDataRowCount());
        assertEquals("999320706", dataset.getDataAsText(0, "Participant ID"));

        beginAt("/study/" + TEST_ASSAY_PRJ_SECURITY + "/" + TEST_ASSAY_FLDR_STUDIES + "/" + TEST_ASSAY_FLDR_STUDY3 + "/dataset.view?datasetId=5001");
        assertEquals(404, getResponseCode());
    }

    @Override
    public void validateQueries(boolean validateSubfolders)
    {
        super.validateQueries(false); // Too many folders
    }
}
