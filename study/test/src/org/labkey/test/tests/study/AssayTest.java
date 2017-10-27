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
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Assays;
import org.labkey.test.categories.BVT;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.components.PropertiesEditor;
import org.labkey.test.pages.AssayDesignerPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.tests.AbstractAssayTest;
import org.labkey.test.tests.AuditLogTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PortalHelper;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({BVT.class, Assays.class})
public class AssayTest extends AbstractAssayTest
{
    private final PortalHelper portalHelper = new PortalHelper(this);

    protected static final String TEST_ASSAY = "Test" + TRICKY_CHARACTERS + "Assay1";
    protected static final String TEST_ASSAY_DESC = "Description for assay 1";

    protected static final String TEST_ASSAY_SET_PROP_EDIT = "NewTargetStudy";
    protected static final String TEST_ASSAY_SET_PROP_NAME = "testAssaySetProp";
    protected static final int TEST_ASSAY_SET_PREDEFINED_PROP_COUNT = 2;
    protected static final FieldDefinition.ColumnType[] TEST_ASSAY_SET_PROP_TYPES = {
            FieldDefinition.ColumnType.Boolean,
            FieldDefinition.ColumnType.Double,
            FieldDefinition.ColumnType.Integer,
            FieldDefinition.ColumnType.DateTime
    };
    protected static final String[] TEST_ASSAY_SET_PROPERTIES = { "false", "100.0", "200", "2001-10-10" };
    protected static final String TEST_ASSAY_RUN_PROP_NAME = "testAssayRunProp";
    protected static final int TEST_ASSAY_RUN_PREDEFINED_PROP_COUNT = 0;
    protected static final FieldDefinition.ColumnType[] TEST_ASSAY_RUN_PROP_TYPES = {
            FieldDefinition.ColumnType.String,
            FieldDefinition.ColumnType.Boolean,
            FieldDefinition.ColumnType.Double,
            FieldDefinition.ColumnType.Integer,
            FieldDefinition.ColumnType.DateTime,
            FieldDefinition.ColumnType.File
    };
    protected static final String TEST_ASSAY_RUN_PROP1 = "TestRunProp";
    protected static final String TEST_ASSAY_DATA_PROP_NAME = "testAssayDataProp";
    protected static final String TEST_ASSAY_DATA_ALIASED_PROP_NAME = "testAssayAliasedData";
    protected static final String ALIASED_DATA = "aliasedData";
    public static final int TEST_ASSAY_DATA_PREDEFINED_PROP_COUNT = 4;
    protected static final FieldDefinition.ColumnType[] TEST_ASSAY_DATA_PROP_TYPES = {
            FieldDefinition.ColumnType.Boolean,
            FieldDefinition.ColumnType.Integer,
            FieldDefinition.ColumnType.DateTime,
            FieldDefinition.ColumnType.String
    };

    protected static final String TEST_RUN1 = "FirstRun";
    protected static final String TEST_RUN1_COMMENTS = "First comments";
    protected static final String TEST_RUN1_DATA1 = "specimenID\tparticipantID\tvisitID\t" + TEST_ASSAY_DATA_PROP_NAME + "4\t" + TEST_ASSAY_DATA_PROP_NAME + "5\t" + TEST_ASSAY_DATA_PROP_NAME + "6\t" + TEST_ASSAY_DATA_ALIASED_PROP_NAME + "\n" +
            "AAA07XK5-05\t\t\ttrue\t\t2000-01-01\t"+ALIASED_DATA+"\n" +
            "AAA07XMC-02\t\t\ttrue\t\t2000-02-02\t"+ALIASED_DATA+"\n" +
            "AAA07XMC-04\t\t\ttrue\t\t2000-03-03\t"+ALIASED_DATA+"\n" +
            "AAA07XSF-02\t\t\tfalse\t\t2000-04-04\t"+ALIASED_DATA+"\n" +
            "AssayTestControl1\te\t5\tfalse\t\t2000-05-05\t"+ALIASED_DATA+"\n" +
            "AssayTestControl2\tf\t6\tfalse\t\t2000-06-06\t"+ALIASED_DATA;

    protected static final String TEST_RUN2 = "SecondRun";
    protected static final String TEST_RUN2_COMMENTS = "Second comments";
    protected static final String TEST_RUN2_DATA1 = "specimenID\tparticipantID\tvisitID\t" + TEST_ASSAY_DATA_PROP_NAME + "20\t" + TEST_ASSAY_DATA_PROP_NAME + "5\t" + TEST_ASSAY_DATA_PROP_NAME + "6\n" +
            "AAA07XK5-05\t\t\ttrue\t20\t2000-01-01\n" +
            "AAA07XMC-02\t\t\ttrue\t19\t2000-02-02\n" +
            "AAA07XMC-04\t\t\ttrue\t18\t2000-03-03\n" +
            "AAA07XSF-02\t\t\tfalse\t17\t2000-04-04\n" +
            "AssayTestControl1\te\t5\tfalse\t16\t2000-05-05\n" +
            "AssayTestControl2\tf\tg\tfalse\t15\t2000-06-06";
    protected static final String TEST_RUN2_DATA2 = "specimenID\tparticipantID\tvisitID\t" + TEST_ASSAY_DATA_PROP_NAME + "4\t" + TEST_ASSAY_DATA_PROP_NAME + "5\t" + TEST_ASSAY_DATA_PROP_NAME + "6\n" +
            "AAA07XK5-05\t\ttrue\t20\t2000-01-01\n" +
            "AAA07XMC-02\t\t\ttrue\t19\t2000-02-02\n" +
            "AAA07XMC-04\t\t\ttrue\t18\t2000-03-03\n" +
            "AAA07XSF-02\t\t\tfalse\t17\t2000-04-04\n" +
            "AssayTestControl1\te\t5\tfalse\t16\t2000-05-05\n" +
            "AssayTestControl2\tf\tg\tfalse\t15\t2000-06-06";
    protected static final String TEST_RUN2_DATA3 = "specimenID\tparticipantID\tvisitID\t" + TEST_ASSAY_DATA_PROP_NAME + "4\t" + TEST_ASSAY_DATA_PROP_NAME + "5\t" + TEST_ASSAY_DATA_PROP_NAME + "6\n" +
            "AAA07XK5-05\t\t\ttrue\t20\t\n" +
            "AAA07XMC-02\t\t\ttrue\t19\t\n" +
            "AAA07XMC-04\t\t\ttrue\t18\t";
    protected static final String TEST_RUN2_DATA4 = "specimenID\tparticipantID\tvisitID\t" + TEST_ASSAY_DATA_PROP_NAME + "4\t" + TEST_ASSAY_DATA_PROP_NAME + "5\t" + TEST_ASSAY_DATA_PROP_NAME + "6\t" + TEST_ASSAY_DATA_ALIASED_PROP_NAME + "\n" +
            "1\tj\t1\t\t\t4/4/06";

    protected static final String TEST_RUN3 = "ThirdRun";
    protected static final String TEST_RUN3_COMMENTS = "Third comments";
    protected static final String TEST_RUN3_DATA1 = "specimenID\tparticipantID\tvisitID\t" + TEST_ASSAY_DATA_PROP_NAME + "4\t" + TEST_ASSAY_DATA_PROP_NAME + "5\t" + TEST_ASSAY_DATA_PROP_NAME + "6\n" +
            "BAQ00051-09\tg\t7\ttrue\t20\t2000-01-01\n" +
            "BAQ00051-08\th\t8\ttrue\t19\t2000-02-02\n" +
            "BAQ00051-11\ti\t9\ttrue\t18\t2000-03-03\n";

    private static final String INVESTIGATOR = "Dr. No";
    private static final String GRANT = "SPECTRE";
    private static final String DESCRIPTION = "World Domination.";

    private final File PROTOCOL_DOC = TestFileUtils.getSampleData("study/Protocol.txt");
    private final File PROTOCOL_DOC2 = TestFileUtils.getSampleData("study/Protocol2.txt");

    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    @Override
    protected String getProjectName()
    {
        return TEST_ASSAY_PRJ_SECURITY;
    }

    /**
     * Cleanup entry point.
     */
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        //should also delete the groups
        _containerHelper.deleteProject(getProjectName(), afterTest);

        _userHelper.deleteUsers(false, TEST_ASSAY_USR_PI1, TEST_ASSAY_USR_TECH1);
    }

    /**
     *  Performs the Assay security test
     *  This test creates a project with a folder hierarchy with multiple groups and users;
     *  defines an Assay at the project level; uploads run data as a labtech; publishes
     *  as a PI, and tests to make sure that security is properly enforced
     */
    @Test
    public void testAssaySecurity() throws Exception
    {
        log("Starting Assay security scenario tests");
        setupEnvironment();
        setupPipeline(getProjectName());
        SpecimenImporter importer = new SpecimenImporter(TestFileUtils.getTestTempDir(), new File(TestFileUtils.getLabKeyRoot(), "/sampledata/study/specimens/sample_a.specimens"), new File(TestFileUtils.getTestTempDir(), "specimensSubDir"), TEST_ASSAY_FLDR_STUDY2, 1);
        importer.importAndWaitForComplete();
        defineAssay();
        uploadRuns(TEST_ASSAY_FLDR_LAB1, TEST_ASSAY_USR_TECH1);
        editResults();
        publishData();
        publishDataToDateBasedStudy();
        publishDataToVisitBasedStudy();
        editAssay();
        viewCrossFolderData();
        verifyStudyList();
        verifyRunDeletionRecallsDatasetRows();
        // TODO: Turn this on once file browser migration is complete.
        //verifyWebdavTree();
        goBack();
    }

    @LogMethod
    private void verifyRunDeletionRecallsDatasetRows()
    {
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        DataRegionTable assayRuns = new DataRegionTable("Runs", this);
        assayRuns.checkCheckbox(0);
        assayRuns.clickHeaderButton("Delete");
        // Make sure that it shows that the data is part of study datasets
        assertTextPresent(TEST_RUN3, "2 dataset(s)", TEST_ASSAY);
        assertTextNotPresent("FirstRun");
        // Do the delete
        clickButton("Confirm Delete");

        // Be sure that we have a special audit record
        clickAndWait(Locator.linkWithText("view copy-to-study history"));
        assertTextPresent("3 row(s) were recalled to the assay: ");

        // Verify that the deleted run data is gone from the dataset
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDY2);
        clickAndWait(Locator.linkWithText("1 dataset"));
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        assertTextPresent("AAA07XMC-04", TEST_RUN1);
        assertTextNotPresent("BAQ00051-09", TEST_RUN3);
    }

    //Issue 12203: Incorrect files are visible from pipeline directory
    private void verifyWebdavTree()
    {
        beginAt("_webdav");
        _fileBrowserHelper.selectFileBrowserItem(getProjectName() + "/Studies/Study 1");
        assertTextPresent("@pipeline", 2);
        Locator.XPathLocator l = Locator.xpath("//span[text()='@pipeline']");
        assertElementPresent(l,  1);
    }

    @LogMethod
    private void editResults()
    {
        // Verify that the results aren't editable by default
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        waitAndClickAndWait(Locator.linkWithText("view results"));
        DataRegionTable table = new DataRegionTable("Data", getDriver());
        assertEquals("No rows should be editable", 0, DataRegionTable.updateLinkLocator().findElements(table.getComponentElement()).size());
        assertElementNotPresent(Locator.button("Delete"));

        // Edit the design to make them editable
        _assayHelper.clickEditAssayDesign(true);
        waitForElement(Locator.xpath("//span[@id='id_editable_results_properties']"), WAIT_FOR_JAVASCRIPT);
        checkCheckbox(Locator.xpath("//span[@id='id_editable_results_properties']/input"));
        clickButton("Save & Close");

        // Try an edit
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickAndWait(Locator.linkWithText("view results"));
        DataRegionTable dataTable = new DataRegionTable("Data", getDriver());
        assertEquals("Incorrect number of results shown.", 10, table.getDataRowCount());
        doAndWaitForPageToLoad(() -> dataTable.updateLink(dataTable.getRowIndex("Specimen ID", "AAA07XK5-05")).click());
        setFormElement(Locator.name("quf_SpecimenID"), "EditedSpecimenID");
        setFormElement(Locator.name("quf_VisitID"), "601.5");
        setFormElement(Locator.name("quf_testAssayDataProp5"), "notAnumber");
        clickButton("Submit");
        assertTextPresent("Could not convert value: " + "notAnumber");
        setFormElement(Locator.name("quf_testAssayDataProp5"), "514801");
        setFormElement(Locator.name("quf_Flags"), "This Flag Has Been Edited");
        clickButton("Submit");
        assertTextPresent("EditedSpecimenID", "601.5", "514801");
        assertElementPresent(Locator.xpath("//img[@src='/labkey/Experiment/flagDefault.gif'][@title='This Flag Has Been Edited']"), 1);
        assertElementPresent(Locator.xpath("//img[@src='/labkey/Experiment/unflagDefault.gif'][@title='Flag for review']"), 9);

        // Try a delete
        dataTable.checkCheckbox(table.getRowIndex("Specimen ID", "EditedSpecimenID"));
        doAndWaitForPageToLoad(() ->
        {
            dataTable.clickHeaderButton("Delete");
            assertAlert("Are you sure you want to delete the selected row?");
        });

        // Verify that the edit was audited
        goToSchemaBrowser();
        viewQueryData("auditLog", "ExperimentAuditEvent");
        assertTextPresent(
                "Data row, id ",
                ", edited in " + TEST_ASSAY + ".",
                "Specimen ID changed from 'AAA07XK5-05' to 'EditedSpecimenID'",
                "Visit ID changed from '601.0' to '601.5",
                "testAssayDataProp5 changed from blank to '514801'",
                "Deleted data row.");
    }

    /**
     * Defines an test assay at the project level for the security-related tests
     */
    @LogMethod
    private void defineAssay()
    {
        log("Defining a test assay at the project level");
        //define a new assay at the project level
        //the pipeline must already be setup
        goToProjectHome();
        portalHelper.addWebPart("Assay List");

        AssayDesignerPage designerPage = _assayHelper.createAssayAndEdit("General", TEST_ASSAY);
        designerPage.setDescription(TEST_ASSAY_DESC);

        for (int i = TEST_ASSAY_SET_PREDEFINED_PROP_COUNT; i < TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + TEST_ASSAY_SET_PROP_TYPES.length; i++)
        {
            designerPage.addBatchField(TEST_ASSAY_SET_PROP_NAME + i, TEST_ASSAY_SET_PROP_NAME + i, TEST_ASSAY_SET_PROP_TYPES[i - TEST_ASSAY_SET_PREDEFINED_PROP_COUNT]);
        }

        for (int i = TEST_ASSAY_RUN_PREDEFINED_PROP_COUNT; i < TEST_ASSAY_RUN_PREDEFINED_PROP_COUNT + TEST_ASSAY_RUN_PROP_TYPES.length; i++)
        {
            designerPage.addRunField(TEST_ASSAY_RUN_PROP_NAME + i, TEST_ASSAY_RUN_PROP_NAME + i, TEST_ASSAY_RUN_PROP_TYPES[i - TEST_ASSAY_RUN_PREDEFINED_PROP_COUNT]);
        }

        for (int i = TEST_ASSAY_DATA_PREDEFINED_PROP_COUNT; i < TEST_ASSAY_DATA_PREDEFINED_PROP_COUNT + TEST_ASSAY_DATA_PROP_TYPES.length; i++)
        {
            designerPage.addDataField(TEST_ASSAY_DATA_PROP_NAME + i, TEST_ASSAY_DATA_PROP_NAME + i, TEST_ASSAY_DATA_PROP_TYPES[i - TEST_ASSAY_DATA_PREDEFINED_PROP_COUNT]);
        }

        designerPage.addDataField("Flags", "Flags", FieldDefinition.ColumnType.Flag);

        // Set some to required
        designerPage.batchFields().selectField(TEST_ASSAY_SET_PREDEFINED_PROP_COUNT).properties().selectValidatorsTab().required.set(true);
        designerPage.batchFields().selectField(TEST_ASSAY_SET_PREDEFINED_PROP_COUNT+1).properties().selectValidatorsTab().required.set(true);
        designerPage.runFields().selectField(0).properties().selectValidatorsTab().required.set(true);
        designerPage.dataFields().selectField(0).properties().selectValidatorsTab().required.set(true);
        designerPage.dataFields().selectField(TEST_ASSAY_DATA_PREDEFINED_PROP_COUNT + 2).properties().selectValidatorsTab().required.set(true);

        // import aliases
        designerPage.dataFields().selectField(TEST_ASSAY_DATA_PREDEFINED_PROP_COUNT + 3).properties().selectAdvancedTab().importAliasesInput.set(TEST_ASSAY_DATA_ALIASED_PROP_NAME);

        designerPage.save();
    }

    /**
     * Generates the text that appears in the target study drop-down for a given study name
     * @param studyName name of the target study
     * @return formatted string of what appears in the target study drop-down
     */
    private String getTargetStudyOptionText(String studyName)
    {
        //the format used in the drop down is:
        // /<project>/<studies>/<study1> (<study> Study)
        return "/" + getProjectName() + "/" + TEST_ASSAY_FLDR_STUDIES + "/" +
                    studyName + " (" + studyName + " Study)";
    }

    /**
     * Uploads run data for the centrally defined Assay while impersonating a labtech-style user
     * @param folder    name of the folder into which we should upload
     * @param asUser    the user to impersonate before uploading
     */
    @LogMethod
    private void uploadRuns(String folder, String asUser)
    {
        log("Uploading runs into folder " + folder + " as user " + asUser);
        navigateToFolder(getProjectName(), folder);
        impersonate(asUser);

        clickAndWait(Locator.linkWithText("Assay List"));
        clickAndWait(Locator.linkWithText(TEST_ASSAY));

        //nav trail check
        assertNavTrail("Assay List", TEST_ASSAY + " Batches");

        clickButton("Import Data");
        assertTextPresent(TEST_ASSAY_SET_PROP_NAME + "3");

        log("Batch properties");
        clickButton("Next");
        assertTextPresent(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 1) + " is required and must be of type Number (Double).");
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 1)), "Bad Test");
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 2)), "Bad Test");
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 3)), "Bad Test");
        clickButton("Next");
        assertTextPresent(
                TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 1) + " must be of type Number (Double).",
                TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 2) + " must be of type Integer.",
                TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 3) + " must be of type Date and Time.");
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 1)), TEST_ASSAY_SET_PROPERTIES[1]);
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 2)), TEST_ASSAY_SET_PROPERTIES[2]);
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 3)), TEST_ASSAY_SET_PROPERTIES[3]);

        //ensure that the target study drop down contains Study 1 and Study 2 only and not Study 3
        //(labtech1 does not have read perms to Study 3)
        waitForElement(Locator.xpath("//option").withText(getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY1)));
        assertElementPresent(Locator.xpath("//option").withText(getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY2)));
        assertElementNotPresent(Locator.xpath("//option").withText(getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY3)));

        //select Study2 as the target study (note that PI is not an Editor in this study so we can test for override case)
        selectOptionByText(Locator.name("targetStudy"), getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY2));

        clickButton("Next");

        log("Check properties set.");
        assertTextPresent(
                TEST_ASSAY_SET_PROPERTIES[1],
                TEST_ASSAY_SET_PROPERTIES[2],
                TEST_ASSAY_SET_PROPERTIES[3],
                TEST_ASSAY_SET_PROPERTIES[0]);

        log("Run properties and data");
        setFormElement(Locator.name(TEST_ASSAY_RUN_PROP_NAME + "5"), PROTOCOL_DOC);
        clickButton("Save and Finish");

        assertTextPresent(TEST_ASSAY_RUN_PROP_NAME + "0 is required and must be of type Text (String).");
        assertTextPresent(PROTOCOL_DOC.getName());
        waitAndClick(Locator.linkWithText("remove"));
        setFormElement(Locator.name("name"), TEST_RUN1);
        setFormElement(Locator.name("comments"), TEST_RUN1_COMMENTS);
        setFormElement(Locator.name(TEST_ASSAY_RUN_PROP_NAME + "0"), TEST_ASSAY_RUN_PROP1);
        clickButton("Save and Finish");

        assertFormElementEquals(Locator.name(TEST_ASSAY_RUN_PROP_NAME + "5"), "");
        assertTextPresent("Data file contained zero data rows");
        click(Locator.xpath("//input[@value='textAreaDataProvider']"));
        setFormElement(Locator.id("TextAreaDataCollector.textArea"), TEST_RUN1_DATA1);
        clickButton("Save and Import Another Run");

        setFormElement(Locator.name("name"), TEST_RUN2);
        setFormElement(Locator.name("comments"), TEST_RUN2_COMMENTS);
        setFormElement(Locator.name(TEST_ASSAY_RUN_PROP_NAME + "0"), TEST_ASSAY_RUN_PROP1);
        setFormElement(Locator.name(TEST_ASSAY_RUN_PROP_NAME + "5"), PROTOCOL_DOC2);
        setFormElement(Locator.id("TextAreaDataCollector.textArea"), TEST_RUN2_DATA1);
        clickButton("Save and Finish");

        assertTextPresent(PROTOCOL_DOC2.getName());
        click(Locator.xpath("//input[@value='textAreaDataProvider']"));
        setFormElement(Locator.id("TextAreaDataCollector.textArea"), TEST_RUN2_DATA2);
        clickButton("Save and Finish");

        assertTextPresent("VisitID must be of type Number (Double)");
        assertTextPresent(PROTOCOL_DOC2.getName());
        assertFormElementEquals(Locator.name("name"), TEST_RUN2);
        assertFormElementEquals(Locator.name("comments"), TEST_RUN2_COMMENTS);
        click(Locator.xpath("//input[@value='textAreaDataProvider']"));
        setFormElement(Locator.id("TextAreaDataCollector.textArea"), TEST_RUN2_DATA3);
        clickButton("Save and Import Another Run");

        assertTextPresent("Missing value for required property: " + TEST_ASSAY_DATA_PROP_NAME + "6");
        click(Locator.xpath("//input[@value='textAreaDataProvider']"));
        setFormElement(Locator.id("TextAreaDataCollector.textArea"), TEST_RUN2_DATA4);
        clickButton("Save and Import Another Run");

        assertFormElementEquals(Locator.name("name"), "");
        assertFormElementEquals(Locator.name("comments"), "");
        setFormElement(Locator.name("name"), TEST_RUN3);
        setFormElement(Locator.name("comments"), TEST_RUN3_COMMENTS);
        setFormElement(Locator.name(TEST_ASSAY_RUN_PROP_NAME + "5"), PROTOCOL_DOC2);
        clickButton("Save and Finish");

        assertTextPresent(PROTOCOL_DOC2.getName().substring(0, PROTOCOL_DOC2.getName().lastIndexOf(".")) + "-1");
        setFormElement(Locator.id("TextAreaDataCollector.textArea"), TEST_RUN3_DATA1);
        clickButton("Save and Finish");

        // Verify the first run did not have a file, the second run had the attached file and the third run had a file
        // with a unique name.
        assertTextNotPresent(PROTOCOL_DOC.getName());
        assertTextPresent(PROTOCOL_DOC2.getName());
        assertTextPresent(PROTOCOL_DOC2.getName().substring(0, PROTOCOL_DOC2.getName().lastIndexOf(".")) + "-1");

        log("Check out the data for one of the runs");
        assertNoLabKeyErrors();
        assertTextPresent(
                TEST_ASSAY + " Runs",
                TEST_ASSAY_RUN_PROP1,
                TEST_ASSAY_SET_PROPERTIES[0],
                TEST_ASSAY_SET_PROPERTIES[3]);
        clickAndWait(Locator.linkWithText(TEST_RUN1));
        assertElementNotPresent(Locator.tagWithText("td", "7.0"));
        // Make sure that our specimen IDs resolved correctly
        assertTextPresent(
                "AAA07XSF-02",
                "999320885",
                "301",
                "AAA07XK5-05",
                "999320812",
                "601",
                TEST_ASSAY_DATA_PROP_NAME + "4",
                TEST_ASSAY_DATA_PROP_NAME + "5",
                TEST_ASSAY_DATA_PROP_NAME + "6",
                "2000-06-06",
                "0.0",
                "f",
                ALIASED_DATA);

        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addColumn("SpecimenID/GlobalUniqueId", "Specimen Global Unique Id");
        _customizeViewsHelper.addColumn("SpecimenID/Specimen/PrimaryType", "Specimen Specimen Primary Type");
        _customizeViewsHelper.addColumn("SpecimenID/AssayMatch", "Specimen Assay Match");
        _customizeViewsHelper.removeColumn("Run/testAssayRunProp1");
        _customizeViewsHelper.removeColumn("Run/Batch/testAssaySetProp2");
        _customizeViewsHelper.removeColumn("testAssayDataProp4");
        _customizeViewsHelper.applyCustomView();

        assertTextPresent("Blood (Whole)", 4);

        Locator.XPathLocator trueLocator = Locator.xpath("//table[contains(@class, 'labkey-data-region')]//td[text() = 'true']");
        int totalTrues = getElementCount(trueLocator);
        assertEquals(4, totalTrues);

        DataRegionTable region = new DataRegionTable("Data", this);
        region.setFilter("SpecimenID", "Starts With", "AssayTestControl");

        // verify that there are no trues showing for the assay match column that were filtered out
        totalTrues = getElementCount(trueLocator);
        assertEquals(0, totalTrues);

        log("Check out the data for all of the runs");
        clickAndWait(Locator.linkWithText("view results"));
        region.clearAllFilters("SpecimenID");
        assertElementPresent(Locator.tagWithText("td", "7.0"));
        assertElementPresent(Locator.tagWithText("td", "18"));

        assertTextPresent("Blood (Whole)", 7);

        Locator.XPathLocator falseLocator = Locator.xpath("//table[contains(@class, 'labkey-data-region')]//td[text() = 'false']");
        int totalFalses = getElementCount(falseLocator);
        assertEquals(3, totalFalses);

        region.setFilter("SpecimenID", "Does Not Start With", "BAQ");

        // verify the falses have been filtered out
        totalFalses = getElementCount(falseLocator);
        assertEquals(0, totalFalses);

        stopImpersonating();
    }

    /**
     * Impersonates the PI user and publishes the data previous uploaded.
     * This will also verify that the PI cannot publish to studies for which
     * the PI does not have Editor permissions.
     */
    @LogMethod
    private void publishData()
    {
        log("Prepare visit map to check PTID counts in study navigator.");
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDY1);
        _studyHelper.goToManageVisits().goToImportVisitMap();
        setFormElement(Locator.name("content"),
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<visitMap xmlns=\"http://labkey.org/study/xml\">\n" +
            "  <visit label=\"Test Visit\" typeCode=\"X\" sequenceNum=\"301.0\" maxSequenceNum=\"302.0\"/>\n" +
            "</visitMap>");
        clickButton("Import");

        log("Publishing the data as the PI");

        //impersonate the PI
        impersonate(TEST_ASSAY_USR_PI1);
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickAndWait(Locator.linkWithText("view results"));

        //select all the data rows and click publish
        DataRegionTable table = new DataRegionTable("Data", this);
        table.checkAllOnPage();
        table.clickHeaderButton("Copy to Study");

        //the target study selected before was Study2, but the PI is not an editor there
        //so ensure that system has correctly caught this fact and now asks the PI to
        //select a different study, and lists only those studies in which the PI is
        //an editor

        //ensure warning
        assertTextPresent("WARNING: You do not have permissions to copy to one or more of the selected run's associated studies.");

        //ensure that Study2 and Study 3 are not available in the target study drop down
        assertElementNotPresent(Locator.xpath("//select[@name='targetStudy']/option[.='" +
                getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY2) + "']"));
        assertElementNotPresent(Locator.xpath("//select[@name='targetStudy']/option[.='" +
                getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY3) + "']"));

        //Study1 is the only one left, so it should be there and already be selected
        assertElementPresent(Locator.xpath("//select[@name='targetStudy']/option[.='" +
                getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY1) + "']"));

        // Make sure the selected study is Study1
        selectOptionByText(Locator.xpath("//select[@name='targetStudy']"), getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY1));

        clickButton("Next");
        assertTextPresent("Copy to " + TEST_ASSAY_FLDR_STUDY1 + " Study: Verify Results");

        setFormElement(Locator.name("visitId"), "301.5");
        clickButton("Copy to Study");

        log("Verifying that the data was published");
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addColumn("QCState", "QC State");
        _customizeViewsHelper.applyCustomView();
        assertTextPresent(
                "Pending Review",
                TEST_RUN1_COMMENTS,
                "2000-01-01");
        clickTab("Overview");
        clickAndWait(Locator.linkWithText("Study Navigator"));

        log("Test participant counts and row counts in study overview");
        String[] row2 = new String[]{TEST_ASSAY, "8", "1", "1", "1", "1", "1", "1", "2"};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});
        // Manually click the checkbox -- normal checkCheckbox() method doesn't seem to work for checkbox that reloads using onchange event
        clickAndWait(Locator.checkboxByNameAndValue("visitStatistic", "RowCount"));
        row2 = new String[]{TEST_ASSAY, "8 / 9", "1 / 1", "1 / 1", "1 / 1", "1 / 1", "1 / 1", "1 / 1", "2 / 3"};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});
        doAndWaitForPageToLoad(() -> uncheckCheckbox(Locator.checkboxByNameAndValue("visitStatistic", "ParticipantCount")));
        row2 = new String[]{TEST_ASSAY, "9", "1", "1", "1", "1", "1", "1", "3"};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});

        clickAndWait(Locator.linkWithText("9"));

        assertElementPresent(Locator.linkWithText("999320885"), 1);
        assertElementPresent(Locator.linkWithText("999320885"), 1);
        assertTextPresent(
                "301.0",
                "9.0",
                "8.0",
                TEST_RUN1_COMMENTS,
                TEST_RUN2_COMMENTS,
                TEST_RUN1,
                TEST_RUN2,
                "2000-06-06",
                TEST_ASSAY_RUN_PROP1,
                "18");

        // test recall
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        waitAndClickAndWait(Locator.linkWithText("view copy-to-study history"));

        // Set a filter so that we know we're recalling SecondRun
        DataRegionTable region = new DataRegionTable("query", this);
        region.setFilter("Comment", "Starts With", "3 row(s) were copied to a study from the assay");
        doAndWaitForPageToLoad(() -> region.detailsLink(region.getRowIndex("Assay/Protocol", TEST_ASSAY)).click());

        DataRegionTable copyStudy = new DataRegionTable("Dataset", this);
        copyStudy.checkAll();
        doAndWaitForPageToLoad(() ->
        {
            copyStudy.clickHeaderButton("Recall Rows");
            acceptAlert();
        });
        assertTextPresent("row(s) were recalled to the assay: " + TEST_ASSAY);

        // Set a filter so that we know we're looking at the copy event for SecondRun again
        region.setFilter("Comment", "Starts With", "3 row(s) were copied to a study from the assay");

        // verify audit entry was adjusted
        doAndWaitForPageToLoad(() -> region.detailsLink(region.getRowIndex("Assay/Protocol", TEST_ASSAY)).click());
        assertTextPresent("All rows that were previously copied in this event have been recalled");

        stopImpersonating();
    }

    /**
     * Designed to test automatic timepoint generation when copying to a date based study.
     * Most tests of timepoint matching are covered by separate junit tests; however,
     * this will create 1 pre-existing timepoint, and when copying data this timepoint should be
     * chosen for appropriate records.
     */
    @LogMethod
    private void publishDataToDateBasedStudy()
    {
        log("Prepare visit map to check PTID counts in study navigator.");

        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDY3);

        clickAndWait(Locator.linkWithText("Manage"));
        clickAndWait(Locator.linkWithText("Manage Timepoints"));
        clickAndWait(Locator.linkWithText("Create New Timepoint"));
        setFormElement(Locator.name("label"), "Preexisting Timepoint");
        setFormElement(Locator.name("sequenceNumMin"), "50");
        setFormElement(Locator.name("sequenceNumMax"), "89");
        selectOptionByText(Locator.name("typeCode"), "Screening");

        clickButton("Save");
        assertElementPresent(Locator.tagWithAttribute("a", "data-original-title", "edit"), 1);

        //select the Lab1 folder and view all the data for the test assay
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickAndWait(Locator.linkWithText("view results"));

        //select all the data rows and click publish
        DataRegionTable table = new DataRegionTable("Data", getDriver());
        table.checkAll();
        table.clickHeaderButton("Copy to Study");

        checkCheckbox(Locator.xpath("//td//input[@type='checkbox']"));

        // Make sure the selected study is Study3
        selectOptionByText(Locator.xpath("//select[@name='targetStudy']"), getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY3));

        clickButton("Next");
        assertTextPresent("Copy to " + TEST_ASSAY_FLDR_STUDY3 + " Study: Verify Results");

        //populate initial set of values and verify the timepoint preview column
        String[] dates = new String[]{"2000-02-02", "2000-03-03", "2000-04-04", "2000-05-05", "2000-06-06", "2001-01-01", "2000-01-01", "2000-02-02", "2000-03-03"};
        int idx = 1;
        for (String d : dates)
        {
            setFormElement(Locator.xpath("(//input[@name='date'])[" + idx + "]"), d);
            idx++;
        }

        setFormElement(Locator.xpath("(//input[@name='participantId'])[1]"), "new1");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[2]"), "new2");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[3]"), "new3");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[4]"), "new4");

        DataRegionTable copyStudy = new DataRegionTable("Data", getDriver());
        copyStudy.clickHeaderButton("Re-Validate");

        //validate timepoints:
        assertElementPresent(Locator.xpath("//td[text()='Day 32 - 39' and following-sibling::td/a[text()='AAA07XMC-02'] and following-sibling::td[text()='301.0']]"));
        assertElementPresent(Locator.xpath("//td[text()='Preexisting Timepoint' and following-sibling::td/a[text()='AAA07XMC-04'] and following-sibling::td[not(text())]]"));
        assertElementPresent(Locator.xpath("//td[text()='Day 90 - 95' and following-sibling::td/a[text()='AAA07XSF-02'] and following-sibling::td[not(text())]]"));

        assertElementPresent(Locator.xpath("//td[text()='Day 120 - 127' and following-sibling::td/a[text()='AssayTestControl1'] and following-sibling::td[text()='5.0']]"));
        assertElementPresent(Locator.xpath("//td[text()='Day 152 - 159' and following-sibling::td/a[text()='AssayTestControl2'] and following-sibling::td[text()='6.0']]"));
        assertElementPresent(Locator.xpath("//td[text()='Day 0 - 7' and following-sibling::td/a[text()='BAQ00051-09'] and following-sibling::td[text()='7.0']]"));
        assertElementPresent(Locator.xpath("//td[text()='Day 32 - 39' and following-sibling::td/a[text()='BAQ00051-08'] and following-sibling::td[text()='8.0']]"));
        assertElementPresent(Locator.xpath("//td[text()='Preexisting Timepoint' and following-sibling::td/a[text()='BAQ00051-11'] and following-sibling::td[text()='9.0']]"));

        copyStudy.clickHeaderButton("Copy to Study");

        log("Verifying that the data was published");
        assertTextPresent(
                TEST_RUN1_COMMENTS,
                "2000-01-01");
        clickTab("Overview");
        clickAndWait(Locator.linkWithText("Study Navigator"));

        log("Test participant counts and row counts in study overview");
        String[] row2 = new String[]{TEST_ASSAY, "9", "1", "2", "2", "1", "1", "1"};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});
        // Manually click the checkbox -- normal checkCheckbox() method doesn't seem to work for checkbox that reloads using onchange event
        clickAndWait(Locator.checkboxByNameAndValue("visitStatistic", "RowCount"));
        row2 = new String[]{TEST_ASSAY, "9 / 9", "1 / 1", "2 / 2", "2 / 2", "1 / 1", "1 / 1", "1 / 1"};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});

        log("Test that correct timepoints were created");

        clickTab("Overview");
        clickAndWait(Locator.linkWithText("Manage Study"));
        clickAndWait(Locator.linkWithText("Manage Timepoints"));
        assertTextPresent(
                "Day 0 - 7",
                "Day 32 - 39",
                "Day 90 - 95",
                "Day 120 - 127",
                "Day 152 - 159");
    }


    /**
     * Designed to test automatic timepoint generation when copying to a date based study.
     * Most tests of timepoint matching are covered by separate junit tests; however,
     * this will create 1 pre-existing timepoint, and when copying data this timepoint should be
     * chosen for appropriate records.
     */
    @LogMethod
    private void publishDataToVisitBasedStudy()
    {
        log("Prepare visit map to check PTID counts in study navigator.");

        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDY2);

        _studyHelper.goToManageVisits().goToImportVisitMap();
        setFormElement(Locator.name("content"),
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<visitMap xmlns=\"http://labkey.org/study/xml\">\n" +
            "  <visit label=\"Test Visit1\" typeCode=\"X\" sequenceNum=\"6.0\" maxSequenceNum=\"13.0\"/>\n" +
            "  <visit label=\"Test Visit2\" typeCode=\"X\" sequenceNum=\"50.0\" maxSequenceNum=\"70.0\"/>\n" +
            "  <visit label=\"Test Visit3\" typeCode=\"X\" sequenceNum=\"302.0\" maxSequenceNum=\"303.0\"/>\n" +
            "</visitMap>"
        );
        clickButton("Import");

        //select the Lab1 folder and view all the data for the test assay
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickAndWait(Locator.linkWithText("view results"));

        //select all the data rows and click publish
        DataRegionTable table = new DataRegionTable("Data", getDriver());
        table.checkAll();
        table.clickHeaderButton("Copy to Study");

        checkCheckbox(Locator.xpath("//td//input[@type='checkbox']"));

        // Make sure the selected study is Study2
        selectOptionByText(Locator.xpath("//select[@name='targetStudy']"), getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY2));

        clickButton("Next");
        assertTextPresent("Copy to " + TEST_ASSAY_FLDR_STUDY2 + " Study: Verify Results");

        //populate initial set of values and verify the timepoint preview column
        String[] visits = new String[]{"302", "33", "4", "70"};
        int idx = 1;
        for (String v : visits)
        {
            setFormElement(Locator.xpath("(//input[@name='visitId'])[" + idx + "]"), v);
            idx++;
        }

        setFormElement(Locator.xpath("(//input[@name='participantId'])[1]"), "new1");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[2]"), "new2");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[3]"), "new3");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[4]"), "new4");

        DataRegionTable copyStudy = new DataRegionTable("Data", getDriver());
        copyStudy.clickHeaderButton("Re-Validate");

        //validate timepoints:
        assertElementPresent(Locator.xpath("//td[text()='Test Visit3' and following-sibling::td/a[text()='AAA07XMC-02']]"));
        assertElementPresent(Locator.xpath("//td[text()='33' and following-sibling::td/a[text()='AAA07XMC-04']]"));
        assertElementPresent(Locator.xpath("//td[text()='4' and following-sibling::td/a[text()='AAA07XSF-02']]"));

        assertElementPresent(Locator.xpath("//td[text()='Test Visit2' and following-sibling::td/a[text()='AssayTestControl1']]"));
        assertElementPresent(Locator.xpath("//td[text()='Test Visit1' and following-sibling::td/a[text()='AssayTestControl2']]"));
        assertElementPresent(Locator.xpath("//td[text()='Test Visit1' and following-sibling::td/a[text()='BAQ00051-09']]"));
        assertElementPresent(Locator.xpath("//td[text()='Test Visit1' and following-sibling::td/a[text()='BAQ00051-08']]"));
        assertElementPresent(Locator.xpath("//td[text()='Test Visit1' and following-sibling::td/a[text()='BAQ00051-11']]"));

        copyStudy.clickHeaderButton("Copy to Study");

        log("Verifying that the data was published");
        assertTextPresent(
                TEST_RUN1_COMMENTS,
                "2000-01-01");
        clickTab("Overview");
        clickAndWait(Locator.linkWithText("Study Navigator"));

        log("Test participant counts and row counts in study overview");
        String[] row2 = new String[]{TEST_ASSAY, "9", " ", " ", " ", "1", " ", " ", "1", " ", " ", "4", " ", " ", " ", " ", "1", "1", " ", " ", " ", "1", " ", " ", " ", " ", " "};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});
        // Manually click the checkbox -- normal checkCheckbox() method doesn't seem to work for checkbox that reloads using onchange event
        clickAndWait(Locator.checkboxByNameAndValue("visitStatistic", "RowCount"));
        row2 = new String[]{TEST_ASSAY, "9 / 9", " ", " ", " ", "1 / 1", " ", " ", "1 / 1", " ", " ", "4 / 4", " ", " ", " ", " ", "1 / 1", "1 / 1", " ", " ", " ", "1 / 1", " ", " ", " ", " ", " "};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});

        log("Test that correct timepoints were created");

        clickTab("Overview");
        _studyHelper.goToManageVisits();
        assertTextPresent(
                "Test Visit1",
                "6.0 - 13.0",
                "Test Visit2",
                "50.0 - 70.0",
                "Test Visit3",
                "302.0 - 303.0");
    }

    /**
     * Tests editing of an existing assay definition
     */
    @LogMethod
    private void editAssay()
    {
        log("Testing edit and delete and assay definition");

        clickProject(getProjectName());

        waitAndClickAndWait(Locator.linkWithText(TEST_ASSAY));
        AssayDesignerPage designerPage = _assayHelper.clickEditAssayDesign();
        PropertiesEditor dataFields = designerPage.dataFields();
        dataFields.selectField(5).setName(TEST_ASSAY_DATA_PROP_NAME + "edit");
        dataFields.selectField(5).setLabel(TEST_ASSAY_DATA_PROP_NAME + "edit");
        dataFields.selectField(4).markForDeletion();
        designerPage.save();

        //ensure that label has changed in run data in Lab 1 folder
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickAndWait(Locator.linkWithText(TEST_RUN1));
        assertTextPresent(TEST_ASSAY_DATA_PROP_NAME + "edit");
        assertTextNotPresent(TEST_ASSAY_DATA_PROP_NAME + 4);

        AuditLogTest.verifyAuditEvent(this, AuditLogTest.ASSAY_AUDIT_EVENT, AuditLogTest.COMMENT_COLUMN, "were copied to a study from the assay: " + TEST_ASSAY, 5);
    }

    @LogMethod
    private void viewCrossFolderData()
    {
        log("Testing cross-folder data");

        clickProject(getProjectName());

        portalHelper.addWebPart("Assay Runs");
        selectOptionByText(Locator.name("viewProtocolId"), "General: " + TEST_ASSAY);
        // assay runs has a details page that needs to be submitted
        clickButton("Submit", defaultWaitForPage);

        // Set the container filter to include subfolders
        DataRegionTable assayRuns = DataRegionTable.findDataRegionWithinWebpart(this, TEST_ASSAY + " Runs");
        assayRuns.setContainerFilter(DataRegionTable.ContainerFilterType.CURRENT_AND_SUBFOLDERS);

        assertTextPresent(TEST_RUN1, TEST_RUN2);

        log("Save the customized view to include subfolders");
        assayRuns = DataRegionTable.findDataRegionWithinWebpart(this, TEST_ASSAY + " Runs");
        CustomizeView customizeViewsHelper = assayRuns.getCustomizeView();
        customizeViewsHelper.openCustomizeViewPanel();
        customizeViewsHelper.saveCustomView("");

        assertTextPresent(TEST_RUN1, TEST_RUN2);

        log("Testing select all data and view");
        assayRuns = DataRegionTable.findDataRegionWithinWebpart(this, TEST_ASSAY + " Runs");
        assayRuns.checkAllOnPage();
        clickButton("Show Results", defaultWaitForPage);
        verifySpecimensPresent(3, 2, 3);

        log("Testing clicking on a run");
        clickProject(getProjectName());
        clickAndWait(Locator.linkWithText(TEST_RUN1));
        verifySpecimensPresent(3, 2, 0);

        clickAndWait(Locator.linkWithText("view results"));
        DataRegionTable region = new DataRegionTable("Data", this);
        region.clearAllFilters("SpecimenID");
        verifySpecimensPresent(3, 2, 3);

        log("Testing assay-study linkage");
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDY1);
        portalHelper.addWebPart("Datasets");
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickButton("View Source Assay", defaultWaitForPage);

        assertTextPresent(TEST_RUN1, TEST_RUN2);

        clickAndWait(Locator.linkWithText(TEST_RUN1));
        verifySpecimensPresent(3, 2, 0);

        clickAndWait(Locator.linkWithText("view results"));
        region = new DataRegionTable("Data", this);
        region.clearAllFilters("SpecimenID");
        verifySpecimensPresent(3, 2, 3);

        // Verify that the correct copied to study column is present
        assertTextPresent("Copied to Study 1 Study");

        log("Testing copy to study availability");
        clickProject(getProjectName());
        clickAndWait(Locator.linkWithText(TEST_RUN3));

        region = new DataRegionTable("Data", this);
        region.checkAll();
        region.clickHeaderButton("Copy to Study");
        clickButton("Next");

        verifySpecimensPresent(0, 0, 3);

        clickButton("Cancel");
    }

    @LogMethod
    private void verifyStudyList()
    {
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDIES);
        portalHelper.addWebPart("Study List");
        assertElementPresent(Locator.linkWithText(TEST_ASSAY_FLDR_STUDY1 + " Study"));
        assertElementPresent(Locator.linkWithText(TEST_ASSAY_FLDR_STUDY2 + " Study"));
        assertElementPresent(Locator.linkWithText(TEST_ASSAY_FLDR_STUDY3 + " Study"));
        portalHelper.clickWebpartMenuItem("Studies", "Customize");

        //verify grid view
        selectOptionByText(Locator.name("displayType"), "Grid");
        clickButton("Submit");
        assertElementNotPresent(Locator.linkWithText("edit"));

        //edit study properties
        clickAndWait(Locator.linkWithText(TEST_ASSAY_FLDR_STUDY1 + " Study"));
        click(Locator.tagWithAttribute("span", "title", "Edit"));
        waitForElement(Locator.name("Investigator"), WAIT_FOR_JAVASCRIPT);
        setFormElement(Locator.name("Investigator"), INVESTIGATOR);
        setFormElement(Locator.name("Grant"), GRANT);
        setFormElement(Locator.name("Description"), DESCRIPTION);
        clickButton("Submit");

        //verify study properties (grid view)
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDIES);
        DataRegionTable table = new DataRegionTable("qwpStudies", this);
        assertEquals("Studies not sorted correctly.", TEST_ASSAY_FLDR_STUDY1 + " Study", table.getDataAsText(0, "Label"));
        assertEquals("Failed to set study investigator.", INVESTIGATOR, table.getDataAsText(0, "Investigator"));
        assertEquals("Failed to set study grant.", GRANT, table.getDataAsText(0, "Grant"));
        assertEquals("Failed to set study description.", DESCRIPTION, table.getDataAsText(0, "Description"));

        //verify study properties (details view)
        portalHelper.clickWebpartMenuItem("Studies", "Customize");
        selectOptionByText(Locator.name("displayType"), "Details");
        clickButton("Submit");
        assertTextPresent(INVESTIGATOR, DESCRIPTION);
        assertTextNotPresent(GRANT, TEST_ASSAY_FLDR_STUDY1 + " Study tracks data"); //Old description
    }

    private void verifySpecimensPresent(int aaa07Count, int controlCount, int baq00051Count)
    {
        // need to double the count, once for the label and once for the param in the link url
        assertTextPresent("AAA07", aaa07Count * 2);
        assertTextPresent("AssayTestControl", controlCount * 2);
        assertTextPresent("BAQ00051", baq00051Count * 2);
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
