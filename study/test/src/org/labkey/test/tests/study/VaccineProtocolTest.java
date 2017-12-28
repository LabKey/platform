/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyA;
import org.labkey.test.pages.DesignerController.DesignerTester;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.ListHelper;
import org.labkey.test.util.PortalHelper;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Category({DailyA.class})
public class VaccineProtocolTest extends BaseWebDriverTest
{
    protected static final String PROJECT_NAME = "ProtocolVerifyProject";
    protected static final String FOLDER_NAME = "My Folder";
    protected static final String TEST_RUN1 = "FirstRun";
    private static final String STUDY_FOLDER = "VaccineStudy";
    private static final String LIST_NAME = "List1";

    private final PortalHelper portalHelper = new PortalHelper(this);

    @Test
    public void testSteps()
    {
        _containerHelper.createProject(PROJECT_NAME, null);
        _containerHelper.createSubfolder(PROJECT_NAME, PROJECT_NAME, FOLDER_NAME, "None", null);

        populateStudyDesignLookups();

        clickFolder(FOLDER_NAME);
        portalHelper.addWebPart("Vaccine Study Protocols");
        clickButton("New Protocol");

        DesignerTester designer = new DesignerTester(this);

        designer.setName(STUDY_FOLDER);
        designer.setInvestigator("My Investigator");
        designer.setGrant("My Grant");
        designer.setSpecies("Rabbit");
        designer.setDescription("This is a very important protocol");

        designer.finishEditing();
        waitForText("No assays have been scheduled.");
        designer.editDesign();

        // set the initial study design information to match the previous defaults, prior to 13.3 changes to remove all default values
        designer.addImmunogen("Cp1", "Canarypox", "1.5e10 Ad vg", "Intramuscular (IM)");
        designer.addImmunogen("gp120", "Subunit Protein", "1.6e8 Ad vg", "Intramuscular (IM)");

        designer.addAdjuvant("Adjuvant1", null, null);
        designer.addAdjuvant("Adjuvant2", null, null);
        designer.addImmunizationGroup("Vaccine", "30");
        designer.addImmunizationGroup("Placebo", "30");

        designer.addImmunizationTimepoint("0");
        designer.addImmunizationTimepoint("28");

        designer.defineFirstUndefinedImmunization(Arrays.asList("Cp1", "Adjuvant1"));
        designer.defineFirstUndefinedImmunization(Arrays.asList("gp120", "Adjuvant1"));
        designer.defineFirstUndefinedImmunization(Arrays.asList("Adjuvant1"));
        designer.defineFirstUndefinedImmunization(Arrays.asList("Adjuvant1"));

        designer.addAssay("ELISPOT", "Schmitz");
        designer.addAssay("Neutralizing Antibodies Panel 1", "Montefiori");
        designer.addAssay("ICS", "McElrath");
        designer.addAssay("ELISA", "Lab 1");

        // change study design information to test GWT UI components
        designer.setImmunogenName(1, "Immunogen1");
        assertTextPresent("Immunogen1|Adjuvant1"); //Make sure that Immunization schedule updated

        designer.addImmunogen("Immunogen3", "Fowlpox", "1.9e8 Ad vg", "Intramuscular (IM)");

        selectOptionByText(Locator.xpath("//table[@id='AntigenGrid3']/tbody/tr[2]/td[3]/select"), "Clade C");

        designer.setImmunizationGroupCount(1, "1");
        designer.setImmunizationGroupCount(2, "2");

        designer.addImmunizationGroup("Vaccine2", "3");

        designer.defineFirstUndefinedImmunization(Arrays.asList("Immunogen3", "Adjuvant1"));
        designer.defineFirstUndefinedImmunization(Arrays.asList("Immunogen3"));

        designer.addAssayTimepoint("Pre-immunization", "0", null);

        designer.setAssayRequiredForTimepoint("Neutralizing Antibodies Panel 1", "Pre-immunization");

        designer.finishEditing();

        waitForText(WAIT_FOR_JAVASCRIPT, "This is a very important protocol");

        assertTextPresent("Immunogen3", "Fowlpox", "Immunogen3|Adjuvant1", "Pre-immunization");

        designer.editDesign();

        waitForText(WAIT_FOR_JAVASCRIPT, "This is a very important protocol");

        designer.addAssayTimepoint(null, "8", null);
        designer.setAssayRequiredForTimepoint("Neutralizing Antibodies Panel 1", "8 days");

        designer.finishEditing();

        waitForText(WAIT_FOR_JAVASCRIPT, "This is a very important protocol");

        clickButton("Create Study Folder");
        setFormElement(Locator.name("beginDate"), "2007-01-01");
        clickButton("Next");
        String cohorts = "SubjectId\tCohort\tStartDate\n" +
                "V1\tVaccine\t2007-01-01\n" +
                "P1\tPlacebo\t2007-06-01\n" +
                "P2\tPlacebo\t2007-06-01\n" +
                "V2\tVaccine2\t2007-11-01\n" +
                "V3\tVaccine2\t2007-11-01\n" +
                "V4\tVaccine2\t2007-11-01";

        setFormElement(Locator.name("participantTSV"), cohorts);
        clickButton("Next");
        String specimens = "Vial Id\tSample Id\tDate\tTimepoint Number\tVolume\tUnits\tSpecimen Type\tderivative_type\tAdditive Type\tSubject Id\n" +
                "V1-0\tV1-0\t2007-01-01\t1.0000\t\t\tBlood\tSerum\t\tV1\n" +
                "P1-0\tP1-0\t2007-06-01\t1.0000\t\t\tBlood\tSerum\t\tP1\n" +
                "P2-0\tP2-0\t2007-06-01\t1.0000\t\t\tBlood\tSerum\t\tP2\n" +
                "V2-0\tV2-0\t2007-11-01\t1.0000\t\t\tBlood\tSerum\t\tV2\n" +
                "V3-0\tV3-0\t2007-11-01\t1.0000\t\t\tBlood\tSerum\t\tV3\n" +
                "V4-0\tV4-0\t2007-11-01\t1.0000\t\t\tBlood\tSerum\t\tV4\n" +
                "V1-8\tV1-8\t2007-01-09\t2.0000\t\t\tBlood\tSerum\t\tV1\n" +
                "P1-8\tP1-8\t2007-06-09\t2.0000\t\t\tBlood\tSerum\t\tP1\n" +
                "P2-8\tP2-8\t2007-11-09\t2.0000\t\t\tBlood\tSerum\t\tP2\n" +
                "V2-8\tV2-8\t2007-11-09\t2.0000\t\t\tBlood\tSerum\t\tV2\n" +
                "V3-8\tV3-8\t2007-11-09\t2.0000\t\t\tBlood\tSerum\t\tV3\n" +
                "V4-8\tV4-8\t2007-11-09\t2.0000\t\t\tBlood\tSerum\t\tV4";
        setFormElement(Locator.name("specimenTSV"), specimens);
        clickButton("Next");
        clickButton("Finish");
        goToFolderManagement();
        clickAndWait(Locator.linkWithText("Folder Type"));
        checkCheckbox(Locator.checkboxByTitle("Experiment"));
        checkCheckbox(Locator.checkboxByTitle("Query"));
        clickButton("Update Folder");

        portalHelper.addWebPart("Lists");
        clickAndWait(Locator.linkWithText("manage lists"));

        ListHelper.ListColumn valueColumn = new ListHelper.ListColumn("Value", "Value", ListHelper.ListColumnType.String, "Vaccine Value");
        _listHelper.createList(getProjectName() + "/" + FOLDER_NAME + "/" + STUDY_FOLDER, LIST_NAME, ListHelper.ListColumnType.Integer, "Key", valueColumn);
        clickButton("Done");

        clickAndWait(Locator.linkWithText(LIST_NAME));
        DataRegionTable.findDataRegion(this).clickInsertNewRow();
        setFormElement(Locator.name("quf_Key"), "1");
        setFormElement(Locator.name("quf_Value"), "One");
        submit();

        setupPipeline(getProjectName());
        defineAssay(getProjectName());
        uploadRun();

        clickAndWait(Locator.linkContainingText(TEST_RUN1));
        click(Locator.name(".toggle"));
        clickButton("Copy to Study");
        clickButton("Next");
        clickButton("Copy to Study");
        navigateToFolder(getProjectName(), STUDY_FOLDER);

        portalHelper.addWebPart("Datasets");
        clickAndWait(Locator.linkWithText("TestAssay1"));
        assertTextPresent("P1", "V3", "V4-8");

/*
        clickAndWait(Locator.linkContainingText("Manage Study"));

        //Resnapshot the data & pick up the new table
        clickButton("Snapshot Study Data");
        clickButton("Create Snapshot");
        clickAndWait(Locator.linkWithText(STUDY_FOLDER +" Study"));

        //Now refresh the schema metadata from the server & make sure we pick up new table
        goToModule("Query");
        _extHelper.clickExtButton(this, "Schema Administration");
        clickAndWait(Locator.linkWithText("reload"));
        assertTextPresent("Schema VerifySnapshot was reloaded successfully.");
        clickAndWait(Locator.linkWithText("Query Schema Browser"));
        selectSchema("VerifySnapshot");
        if (isQueryPresent("VerifySnapshot", "TestAssay1"))
            viewQueryData("VerifySnapshot", "TestAssay1");
        else if (isQueryPresent("VerifySnapshot", "testassay1"))
            viewQueryData("VerifySnapshot", "testassay1");
        else
            fail("TestAssay1 table not present");
*/

        navigateToFolder(getProjectName(), STUDY_FOLDER);
        clickAndWait(Locator.linkWithText("Study Navigator"));
        assertTextPresent("Day 12");
        EditDatasetDefinitionPage editDatasetPage = _studyHelper
                .goToManageDatasets()
                .clickCreateNewDataset()
                .setName("Simple")
                .submit();
        editDatasetPage.getFieldsEditor()
                .selectField(0).setName("Value");
        editDatasetPage
                .save()
                .clickViewData()
                .getDataRegion()
                .clickImportBulkData();
        _listHelper.submitTsvData("participantid\tDate\tValue\treplace\nP1\t2/1/2007\tHello\nPnew\t11/17/2007\tGoodbye");

        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addColumn("Day");
        _customizeViewsHelper.applyCustomView();
        assertTextPresent("-120", "320");
        navigateToFolder(getProjectName(), STUDY_FOLDER);
        clickAndWait(Locator.linkWithText("Study Navigator"));
        assertTextPresent("Day 320");
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Timepoints"));
        setFormElement(Locator.name("startDate"), "2007-11-01");
        submit();
        navigateToFolder(getProjectName(), STUDY_FOLDER);
        clickAndWait(Locator.linkWithText("Study Navigator"));
        //Make sure our guy picked up the new study start date
        assertTextPresent("Day 16");
        navigateToFolder(getProjectName(), STUDY_FOLDER);
        clickAndWait(Locator.linkWithText("Subjects"));
        DataRegionTable.findDataRegion(this).clickImportBulkData();
        _listHelper.submitTsvData("participantid\tDate\tCohort\tStartDate\nPnew\t11/7/2007\tPlacebo\t11/7/2007");
        navigateToFolder(getProjectName(), STUDY_FOLDER);
        clickAndWait(Locator.linkWithText("Study Navigator"));
        //Make sure our guy picked up the his personal start date
        assertTextPresent("Day 10");
    }

    protected static final String TEST_ASSAY = "TestAssay1";
    protected static final String TEST_ASSAY_DESC = "Description for assay 1";
    protected static final String TEST_ASSAY_SET_PROP_EDIT = "NewTargetStudy";
    protected static final String TEST_ASSAY_SET_PROP_NAME = "testAssaySetProp";
    protected static final int TEST_ASSAY_SET_PREDEFINED_PROP_COUNT = 2;
    protected static final int TEST_ASSAY_DATA_PREDEFINED_PROP_COUNT = 4;
    protected static final String[] TEST_ASSAY_DATA_PROP_NAMES = {"Value" };
    protected static final ListHelper.ListColumnType[] TEST_ASSAY_DATA_PROP_TYPES = { ListHelper.ListColumnType.Integer };
    // protected final static int WAIT_FOR_JAVASCRIPT = 5000;  uncomment to override base class

    /**
     * Sets up the data pipeline for the specified project. This can be called from any page.
     * @param project name of project for which the pipeline should be setup
     */
    protected void setupPipeline(String project)
    {
        log("Setting up data pipeline for project " + project);
        clickProject(project);
        portalHelper.addWebPart("Data Pipeline");
        clickButton("Setup");
        File dir = TestFileUtils.getTestTempDir();
        dir.mkdirs();

        setPipelineRoot(dir.getAbsolutePath());

        //make sure it was set
        assertTextPresent("The pipeline root was set to '" + dir.getAbsolutePath());
    } //setupPipeline

    /**
     * Defines an test assay at the project level for the security-related tests
     */
    protected void defineAssay(String projectName)
    {
        log("Defining a test assay at the project level");
        //define a new assay at the project level
        //the pipeline must already be setup
        clickProject(projectName);
        portalHelper.addWebPart("Assay List");

        _assayHelper.uploadXarFileAsAssayDesign(TestFileUtils.getSampleData("studyextra/TestAssay1.xar"), 1);
        goToProjectHome();

    } //defineAssay()

    /**
     * Generates the text that appears in the target study drop-down for a given study name
     * @param studyName name of the target study
     * @return formatted string of what appears in the target study drop-down
     */
    protected String getTargetStudyOptionText(String projectName, String folderName, String studyName)
    {
        //the format used in the drop down is:
        // /<project>/<studies>/<study1> (<study> Study)
        return "/" + projectName + "/" + folderName + "/" +
                    studyName + " (" + studyName + " Study)";
    } //getTargetStudyOptionText()

    protected static final String TEST_RUN1_COMMENTS = "First comments";
    protected static final File TEST_RUN1_DATA1 = TestFileUtils.getSampleData("studyextra/TestAssayRun1.tsv");

    protected void uploadRun()
    {
        clickAndWait(Locator.linkWithText("Assay List"));
        clickAndWait(Locator.linkWithText(TEST_ASSAY));

        clickButton("Import Data");
        selectOptionByText(Locator.xpath("//select[@name='targetStudy']"), getTargetStudyOptionText(PROJECT_NAME, FOLDER_NAME, STUDY_FOLDER));
        click(Locator.radioButtonByNameAndValue("participantVisitResolver", "SampleInfo"));
        clickButton("Next");


        log("Run properties and data");
        setFormElement(Locator.name("name"), TEST_RUN1);
        setFormElement(Locator.name("comments"), TEST_RUN1_COMMENTS);
        click(Locator.xpath("//input[@value='textAreaDataProvider']"));
        setFormElement(Locator.id("TextAreaDataCollector.textArea"), TestFileUtils.getFileContents(TEST_RUN1_DATA1));
        clickButton("Save and Finish");
        // reenable the following lines when we've moved to strict type checking of the incoming file.  For now, we're
        // flexible and only error if required columns are missing.
    }

    private void populateStudyDesignLookups()
    {
        goToProjectHome();
        portalHelper.addWebPart("Vaccine Study Protocols");

        goToAssayConfigureLookupValues(0); // index 0 = Immunogen Types
        for (String immunogenType : new String[]{"Canarypox", "Fowlpox", "Subunit Protein"})
            insertLookupRecord(immunogenType, immunogenType + " Label");

        goToAssayConfigureLookupValues(1); // index 1 = Routes
        for (String route : new String[]{"Intramuscular (IM)"})
            insertLookupRecord(route, route + " Label");

        goToAssayConfigureLookupValues(2); // index 2 = Genes
        for (String gene : new String[]{"Gag", "Env"})
            insertLookupRecord(gene, gene + " Label");

        goToAssayConfigureLookupValues(3); // index 3 = SubTypes
        for (String subType : new String[]{"Clade B", "Clade C"})
            insertLookupRecord(subType, subType + " Label");

        goToAssayConfigureLookupValues(4); // index 4 = Assays
        for (String assay : new String[]{"ELISPOT", "Neutralizing Antibodies Panel 1", "ICS", "ELISA"})
            insertLookupRecord(assay, assay + " Label");

        goToAssayConfigureLookupValues(5); // index 5 = Labs
        for (String lab : new String[]{"Schmitz", "Montefiori", "McElrath", "Lab 1"})
            insertLookupRecord(lab, lab + " Label");
    }

    private void goToAssayConfigureLookupValues(int index)
    {
        goToProjectHome();
        clickButton("New Protocol");
        waitForText("Configure Dropdown Options");
        click(Locator.linkContainingText("Configure Dropdown Options"));
        clickAndWait(Locator.linkWithText("project").index(index));
    }

    private void insertLookupRecord(String name, String label)
    {
        DataRegionTable.findDataRegion(this).clickInsertNewRow();
        if (name != null) setFormElement(Locator.name("quf_Name"), name);
        if (label != null) setFormElement(Locator.name("quf_Label"), label);
        clickButton("Submit");
    }

    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
