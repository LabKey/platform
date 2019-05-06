/*
 * Copyright (c) 2007-2018 LabKey Corporation
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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Assays;
import org.labkey.test.categories.DailyC;
import org.labkey.test.pages.AssayDesignerPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.tests.AbstractAssayTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PortalHelper;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category({DailyC.class, Assays.class})
public class CopyAssayToStudyTest extends AbstractAssayTest
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
        return "Copy to Study Test";
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

    @BeforeClass
    public static void setupProject() throws Exception
    {
        CopyAssayToStudyTest initTest = (CopyAssayToStudyTest) getCurrentTest();
        initTest.doSetup(); // Perform shared setup steps here
    }

    private void doSetup() throws IOException
    {
        setupEnvironment();
        setupPipeline(getProjectName());
        SpecimenImporter importer = new SpecimenImporter(TestFileUtils.getTestTempDir(),
                new File(TestFileUtils.getLabKeyRoot(), "/sampledata/study/specimens/sample_a.specimens"),
                new File(TestFileUtils.getTestTempDir(), "specimensSubDir"), TEST_ASSAY_FLDR_STUDY2, 1);
        importer.importAndWaitForComplete();
        defineAssay();

        uploadRuns(TEST_ASSAY_FLDR_LAB1, TEST_ASSAY_USR_TECH1);
    }

    @Test
    public void verifyAsyncCopyToAssay() throws IOException
    {
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        DataRegionTable assayRuns = new DataRegionTable("Runs", this);
        assayRuns.checkAllOnPage();

        assayRuns.clickHeaderButton("Copy to Study");
        waitForElement(Locator.checkboxById("autoCopy"));
        checkCheckbox(Locator.checkboxById("autoCopy"));
        clickButton("Next");

        waitForText("Automatic copying of assay data to study");
        assertTrue("expect to be in pipeline-status for Lab 1", getDriver().getCurrentUrl().contains("pipeline-status"));

        clickButton("Show Grid");
        waitForPipelineJobsToComplete(1, false);
        clickAndWait(Locator.linkWithText("Automatic copying of assay data to study"));
        DataRegionTable dataSet = new DataRegionTable("Dataset", this);
        List<String> specimenIds = Arrays.asList("AAA07XMC-02", "AAA07XMC-04", "AAA07XK5-05", "AAA07XSF-02",
                "AssayTestControl1", "AssayTestControl2", "BAQ00051-09", "BAQ00051-08", "BAQ00051-11", "1");

        assertEquals("expected copied rows should be for ", specimenIds, dataSet.getColumnDataAsText("Specimen ID"));
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

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
