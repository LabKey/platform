/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.test.tests;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Study;
import org.labkey.test.pages.studydesigncontroller.ManageAssayScheduleTester;
import org.labkey.test.pages.studydesigncontroller.ManageTreatmentsTester;
import org.labkey.test.pages.studydesigncontroller.ManageStudyProductsTester;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PortalHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by tchadick on 1/29/14.
 */
@Category({DailyB.class, Study.class})
public class StudyProtocolDesignerTest extends BaseWebDriverTest
{
    private static final File STUDY_ARCHIVE = new File(TestFileUtils.getSampledataPath(), "study/CohortStudy.zip");
    // Cohorts: defined in study archive
    private static final String[] COHORTS = {"Positive", "Negative"};

    private static final File FOLDER_ARCHIVE = new File(TestFileUtils.getSampledataPath(), "FolderExport/ProtocolLookup.folder.zip");
    // lookups: defined in folder archive
    private static final String[] IMMUNOGEN_TYPES = {"Canarypox", "Fowlpox", "Subunit Protein"};
    private static final String[] GENES = {"Env", "Gag"};
    private static final String[] SUBTYPES = {"Clade B", "Clade C"};
    private static final String[] ROUTES = {"Intramuscular (IM)"};
    private static final String[] LABS = {"Lab 1", "McElrath", "Montefiori", "Schmitz"};
    private static final String[] SAMPLE_TYPES = {"Platelets", "Plasma"};

    // Study design elements created by this test
    private static final String[] IMMUNOGENS = {"gp100", "Cp1", "Immunogen1"};
    private static final String[] ANTIGENS = {};
    private static final String[] ADJUVANTS = {"Adjuvant1", "Freund's incomplete"};
    private static final String[] DOSE_AND_UNITS = {"35ug", "1.6e8 Ad vg"};
    private static final String[] TREATMENTS = {"Treatment1", "Treatment2"};
    private static List<ManageTreatmentsTester.Visit> VISITS = new ArrayList<>();
    private static List<ManageTreatmentsTester.Visit> NEW_VISITS = new ArrayList<>();
    private static final String[] NEW_ASSAYS = {"Elispot", "Neutralizing Antibodies", "ICS"};
    private static final String[] NEW_COHORTS = {"TestCohort", "OtherTestCohort"};
    
    public StudyProtocolDesignerTest()
    {
        super();
        VISITS.add(new ManageTreatmentsTester.Visit("Enrollment"));
        VISITS.add(new ManageTreatmentsTester.Visit("Visit 1"));
        VISITS.add(new ManageTreatmentsTester.Visit("Visit 2"));
        VISITS.add(new ManageTreatmentsTester.Visit("Visit 3"));
        VISITS.add(new ManageTreatmentsTester.Visit("Visit 4"));
        NEW_VISITS.add(new ManageTreatmentsTester.Visit("NewVisit1", 6, 7));
        NEW_VISITS.add(new ManageTreatmentsTester.Visit("NewVisit2", 8, 8));
    }

    @BeforeClass
    public static void doSetup() throws Exception
    {
        StudyProtocolDesignerTest initTest = (StudyProtocolDesignerTest)getCurrentTest();

        initTest._containerHelper.createProject(initTest.getProjectName(), null);
        initTest.importFolderFromZip(FOLDER_ARCHIVE);

        initTest._containerHelper.createSubfolder(initTest.getProjectName(), initTest.getFolderName(), "Study");
        initTest.importStudyFromZip(STUDY_ARCHIVE);

        initTest.clickTab("Overview");
        PortalHelper portalHelper = new PortalHelper(initTest);
        portalHelper.addWebPart("Vaccine Design");
        portalHelper.addWebPart("Immunization Schedule");
        portalHelper.addWebPart("Assay Schedule");
    }

    @Before
    public void preTest()
    {
        clickProject(getProjectName());
        clickFolder(getFolderName());
    }

    @Test
    public void testStudyProtocolDesigner()
    {
        testVaccineDesign();
        preTest();
        testTreatmentSchedule();
        preTest();
        testAssaySchedule();
        testExportImport();
    }

    @LogMethod
    public void testVaccineDesign()
    {
        Locator editButton = PortalHelper.Locators.webPart("Vaccine Design").append(Locator.lkButton("Edit"));
        clickAndWait(editButton);
        assertElementPresent(Locator.linkWithText("Manage Treatments"));

        ManageStudyProductsTester vaccineDesign = new ManageStudyProductsTester(this);

        vaccineDesign.insertNewImmunogen(IMMUNOGENS[0], IMMUNOGEN_TYPES[0]);
        vaccineDesign.insertNewImmunogen(IMMUNOGENS[1], IMMUNOGEN_TYPES[1]);
        vaccineDesign.insertNewImmunogen(IMMUNOGENS[2], IMMUNOGEN_TYPES[2]);

        vaccineDesign.editAntigens(IMMUNOGENS[1]);
        vaccineDesign.insertNewAntigen(IMMUNOGENS[1], GENES[0], SUBTYPES[0], null, null);
        vaccineDesign.insertNewAntigen(IMMUNOGENS[1], GENES[1], SUBTYPES[1], null, null);
        vaccineDesign.submitAntigens(IMMUNOGENS[1]);

        vaccineDesign.insertNewAdjuvant(ADJUVANTS[0]);
        vaccineDesign.insertNewAdjuvant(ADJUVANTS[1]);
    }

    @LogMethod
    public void testTreatmentSchedule()
    {
        Locator editButton = PortalHelper.Locators.webPart("Immunization Schedule").append(Locator.lkButton("Edit"));
        clickAndWait(editButton);

        ManageTreatmentsTester treatments = new ManageTreatmentsTester(this);

        treatments.insertNewTreatment(TREATMENTS[0], null,
                new ManageTreatmentsTester.TreatmentComponent(IMMUNOGENS[0], DOSE_AND_UNITS[0], ROUTES[0]),
                new ManageTreatmentsTester.TreatmentComponent(IMMUNOGENS[1], DOSE_AND_UNITS[1], ROUTES[0]));

        treatments.insertNewTreatment(TREATMENTS[1], null,
                new ManageTreatmentsTester.TreatmentComponent(IMMUNOGENS[2], DOSE_AND_UNITS[1], ROUTES[0]));


        treatments.insertNewCohort(NEW_COHORTS[0], 2,
                new ManageTreatmentsTester.TreatmentVisit(TREATMENTS[0], VISITS.get(0), false),
                new ManageTreatmentsTester.TreatmentVisit(TREATMENTS[1], VISITS.get(2), false));

        treatments.insertNewCohort(NEW_COHORTS[1], 5,
                new ManageTreatmentsTester.TreatmentVisit(TREATMENTS[0], VISITS.get(0), false),
//                new ManageTreatmentsTester.TreatmentVisit(TREATMENTS[1], NEW_VISITS.get(0), true), //TODO: Creating a second new visit triggers a page load
                new ManageTreatmentsTester.TreatmentVisit(TREATMENTS[1], NEW_VISITS.get(1), true));

        treatments.addTreatmentVisitMappingsToExistingCohort(COHORTS[0],
                new ManageTreatmentsTester.TreatmentVisit(TREATMENTS[0], VISITS.get(0), false),
                new ManageTreatmentsTester.TreatmentVisit(TREATMENTS[1], NEW_VISITS.get(0), true),
                new ManageTreatmentsTester.TreatmentVisit(TREATMENTS[0], VISITS.get(1), false));
    }

    @LogMethod
    public void testAssaySchedule()
    {
        Locator editButton = PortalHelper.Locators.webPart("Assay Schedule").append(Locator.lkButton("Edit"));
        clickAndWait(editButton);
        _ext4Helper.waitForMaskToDisappear();

        ManageAssayScheduleTester schedule = new ManageAssayScheduleTester(this);

        schedule.insertNewAssayConfiguration(NEW_ASSAYS[0], null, LABS[0], null);
        schedule.insertNewAssayConfiguration(NEW_ASSAYS[1], null, LABS[1], null);
        schedule.insertNewAssayConfiguration(NEW_ASSAYS[2], null, LABS[2], null);
        schedule.insertNewAssayConfiguration(NEW_ASSAYS[2], null, LABS[3], null);

        checkCheckbox(ManageAssayScheduleTester.Locators.assayScheduleGridCheckbox(NEW_ASSAYS[0], VISITS.get(0).getLabel()));
        checkCheckbox(ManageAssayScheduleTester.Locators.assayScheduleGridCheckbox(NEW_ASSAYS[1], VISITS.get(1).getLabel()));
        checkCheckbox(ManageAssayScheduleTester.Locators.assayScheduleGridCheckbox(NEW_ASSAYS[2], VISITS.get(2).getLabel()));
        checkCheckbox(ManageAssayScheduleTester.Locators.assayScheduleGridCheckbox(NEW_ASSAYS[0], NEW_VISITS.get(0).getLabel()));
        checkCheckbox(ManageAssayScheduleTester.Locators.assayScheduleGridCheckbox(NEW_ASSAYS[1], NEW_VISITS.get(1).getLabel()));

        schedule.setAssayPlan("Do some exciting science!");
    }

    @LogMethod
    public void testExportImport()
    {
        final String importedFolder = "Imported " + getFolderName();
        File downloadedFolder = exportFolderToBrowserAsZip();

        _containerHelper.createSubfolder(getProjectName(), importedFolder, null);
        importFolderFromZip(downloadedFolder);

        verifyImportedProtocol(importedFolder);
    }

    @LogMethod
    private void verifyImportedProtocol(String folderName)
    {
        clickFolder(folderName);

        verifyImmunogenTable();
        verifyAdjuvantTable();
        verifyTreatmentSchedule();
        verifyAssaySchedule();
    }

    @LogMethod(quiet = true)
    private void verifyImmunogenTable()
    {
        waitForElementToDisappear(Locator.css("div.immunogen-hiv-antigen").withText("...")); // HIV Antigen placeholder
        Locator.XPathLocator antigenGrid = Locator.tagWithClass("div", "immunogen-hiv-antigen").append("/table");
        assertElementPresent(antigenGrid, 1);

        Locator.XPathLocator immunogenGrid = Locators.studyProtocolWebpartGrid("Immunogens");
        String gridText = getText(immunogenGrid);

        List<String> expectedImmunogenTexts = new ArrayList<>();
        expectedImmunogenTexts.addAll(Arrays.asList(IMMUNOGENS));
        expectedImmunogenTexts.addAll(Arrays.asList(IMMUNOGEN_TYPES));
        expectedImmunogenTexts.addAll(Arrays.asList(GENES));
        expectedImmunogenTexts.addAll(Arrays.asList(SUBTYPES));

        for (String expectedText : expectedImmunogenTexts)
        {
            assertTrue("Vaccine design immunogens did not contain: " + expectedText, gridText.contains(expectedText));
        }
    }

    @LogMethod(quiet = true)
    private void verifyAdjuvantTable()
    {
        Locator.XPathLocator adjuvantGrid = Locators.studyProtocolWebpartGrid("Adjuvants");
        String gridText = getText(adjuvantGrid);

        for (String expectedText : ADJUVANTS)
        {
            assertTrue("Vaccine design adjuvants did not contain: " + expectedText, gridText.contains(expectedText));
        }
    }

    @LogMethod(quiet = true)
    private void verifyTreatmentSchedule()
    {
        Locator.XPathLocator scheduleGrid = Locators.studyProtocolWebpartGrid("Immunization Schedule");
        String gridText = getText(scheduleGrid);

        List<String> expectedTexts = new ArrayList<>();
        expectedTexts.addAll(Arrays.asList(COHORTS));
        expectedTexts.addAll(Arrays.asList(NEW_COHORTS));
        expectedTexts.addAll(Arrays.asList(TREATMENTS));
        expectedTexts.addAll(Arrays.asList(new String[]{"Visit 1", "Visit 2", "NewVisit1", "NewVisit2"}));

        for (String expectedText : expectedTexts)
        {
            assertTrue("Vaccine design treatment schedule did not contain: " + expectedText, gridText.contains(expectedText));
        }

        assertEquals("Wrong number of scheduled treatment/visits", 4, StringUtils.countMatches(gridText, TREATMENTS[0]));
        assertEquals("Wrong number of scheduled treatment/visits", 3, StringUtils.countMatches(gridText, TREATMENTS[1]));
    }

    @LogMethod(quiet = true)
    private void verifyAssaySchedule()
    {
        Locator.XPathLocator scheduleGrid = Locators.studyProtocolWebpartGrid("Assay Schedule");
        String gridText = getText(scheduleGrid);

        List<String> expectedTexts = new ArrayList<>();
        expectedTexts.addAll(Arrays.asList(NEW_ASSAYS));
        expectedTexts.addAll(Arrays.asList(LABS));
        expectedTexts.addAll(Arrays.asList(new String[]{"Enrollment", "Visit 1", "Visit 2", "NewVisit1", "NewVisit2"}));

        for (String expectedText : expectedTexts)
        {
            assertTrue("Vaccine design assay schedule did not contain: " + expectedText, gridText.contains(expectedText));
        }

        assertEquals("Wrong number of scheduled assay/visits", 5, StringUtils.countMatches(gridText, "\u2713"));
    }

    @Nullable
    @Override
    protected String getProjectName()
    {
        return "StudyProtocolDesignerTest Project";
    }

    protected String getFolderName()
    {
        return "ProtocolDesigner Study";
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

    public static class Locators
    {
        public static Locator.XPathLocator studyProtocolWebpartGrid(String title)
        {
            return Locator.tagWithClass("table", "labkey-data-region").withPredicate(Locator.tagWithClass("div", "study-vaccine-design-header").withText(title));
        }
    }
}
