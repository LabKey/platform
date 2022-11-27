package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.Daily;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.components.domain.DomainFormPanel;
import org.labkey.test.pages.study.DatasetDesignerPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.StudyHelper;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 4)
public class StudyDateAndContinuousTimepointTest extends BaseWebDriverTest
{
    private static final String VISIT_DATE = new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime());
    private static final String datasetName = "SampleDataset";

    @BeforeClass
    public static void doSetup()
    {
        StudyDateAndContinuousTimepointTest init = (StudyDateAndContinuousTimepointTest) getCurrentTest();
        init.doCreateSteps();
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Nullable
    @Override
    protected String getProjectName()
    {
        return "StudyDateAndContinuousTimepointTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    private void doCreateSteps()
    {
        _containerHelper.createProject(getProjectName(), "Study");
        clickButton("Create Study");
        click(Locator.radioButtonById("continuousTimepointType"));
        clickButton("Create Study");

        goToProjectHome();
        PortalHelper portalHelper = new PortalHelper(this);
        portalHelper.addWebPart("Datasets");

        log("Creating a new dataset");
        DatasetDesignerPage editDatasetPage = _studyHelper
                .goToManageDatasets()
                .clickCreateNewDataset()
                .setName(datasetName);

        DomainFormPanel fieldsEditor = editDatasetPage.getFieldsPanel();
        fieldsEditor.manuallyDefineFields(new FieldDefinition("TestDate", FieldDefinition.ColumnType.DateAndTime).setLabel("TestDate"));
        editDatasetPage.clickSave();

        log("Inserting rows in the dataset");
        goToProjectHome();
        clickAndWait(Locator.linkWithText(datasetName));
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        table.clickInsertNewRow();
        setFormElement(Locator.name("quf_ParticipantId"), "P1");
        setFormElement(Locator.name("quf_date"), VISIT_DATE);
        setFormElement(Locator.name("quf_TestDate"), VISIT_DATE);
        clickButton("Submit");
    }

    @Test
    public void testVisitWithAllTimepointTypes()
    {
        log("Changing the timepoint to date");
        goToProjectHome();
        changeTimepointType("date");

        log("Verifying the visit column in dataset");
        goToProjectHome();
        clickAndWait(Locator.linkWithText(datasetName));
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        CustomizeView tableCustomizeView = table.getCustomizeView();
        tableCustomizeView.openCustomizeViewPanel();
        waitForText("Available Fields");
        tableCustomizeView.addColumn(new String[]{"ParticipantVisit", "Visit"});
        tableCustomizeView.saveDefaultView();
        checker().verifyEquals("Visit field is not blank when study is changed to date", Arrays.asList("Day 0"),
                table.getColumnDataAsText("ParticipantVisit/Visit"));

        log("Changing the timepoint to continuous and verifying visit");
        goToProjectHome();
        changeTimepointType("continuous");
        clickAndWait(Locator.linkWithText(datasetName));
        table = new DataRegionTable("Dataset", getDriver());
        checker().verifyEquals("Visit field is blank when study is changed to continuous", Arrays.asList(" "),
                table.getColumnDataAsText("ParticipantVisit/Visit"));

        log("Changing the timepoint to date and verifying date is recalculated");
        goToProjectHome();
        changeTimepointType("date");
        clickAndWait(Locator.linkWithText(datasetName));
        table = new DataRegionTable("Dataset", getDriver());
        checker().verifyEquals("Visit field is not blank when study is changed to date", Arrays.asList("Day 0"),
                table.getColumnDataAsText("ParticipantVisit/Visit"));
    }

    @Test
    public void testPublishStudy()
    {
        String publishedFolder = "Published study - Continuous";
        goToProjectHome();
        changeTimepointType("date");

        log("Publish study.");
        clickTab("Manage");

        clickButton("Publish Study", 0);
        _extHelper.waitForExtDialog("Publish Study");

        checker().verifyTrue("Date timepoint value should be present",
                isElementPresent(Locator.tagWithId("input", "dateRadio")));

        checker().verifyTrue("Continuous timepoint value should be present",
                isElementPresent(Locator.tagWithId("input", "continuousRadio")));

        setFormElement(Locator.name("studyName"), publishedFolder);
        clickButton("Next", 0);

        //participants
        _studyHelper.advanceThroughPublishStudyWizard(StudyHelper.Panel.studyWizardParticipantList);

        //Datasets
        _extHelper.selectExtGridItem("Label", datasetName, -1, "studyWizardDatasetList", true);
        clickButton("Next", 0);

        //Timepoints
        if (isElementPresent(Locator.xpath("//div[@class = 'labkey-nav-page-header'][text() = 'Timepoints']")))
        {
            waitForElement(Locator.xpath("//div[@class = 'labkey-nav-page-header'][text() = 'Timepoints']"));
            waitForElement(Locator.css(".studyWizardVisitList"));
            _extHelper.selectExtGridItem("Label", "Day 0", -1, "studyWizardVisitList", true);
            clickButton("Next", 0);
        }

        //specimens, if present & active
        if (_studyHelper.isSpecimenModuleActive())
            _studyHelper.advanceThroughPublishStudyWizard(StudyHelper.Panel.studySpecimens);

        List<StudyHelper.IPanel> wizardPanels = Arrays.asList(
                StudyHelper.Panel.studyObjects, StudyHelper.Panel.studyWizardListList, StudyHelper.Panel.studyWizardQueryList,
                StudyHelper.Panel.studyWizardViewList, StudyHelper.Panel.studyWizardReportList, StudyHelper.Panel.folderObjects,
                StudyHelper.Panel.studyWizardPublishOptionsList);
        _studyHelper.advanceThroughPublishStudyWizard(wizardPanels);

        waitForPipelineJobsToComplete(1, "publish study", false);

        goToProjectHome();
        clickFolder(publishedFolder);
        clickAndWait(Locator.linkContainingText("dataset"));
        clickAndWait(Locator.linkWithText(datasetName));
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        CustomizeView tableCustomizeView = table.getCustomizeView();
        tableCustomizeView.openCustomizeViewPanel();
        waitForText("Available Fields");
        tableCustomizeView.addColumn(new String[]{"ParticipantVisit", "Visit"});
        tableCustomizeView.saveDefaultView();
        checker().verifyEquals("Visit field is not Day 0 when study is changed to date", Arrays.asList("Day 0"),
                table.getColumnDataAsText("ParticipantVisit/Visit")); //Needs to be updated when related bug is fixed.
    }

    private void changeTimepointType(String type)
    {
        clickTab("Manage");
        waitAndClick(Locator.linkContainingText("Study Properties"));
        waitForElement(Locator.id(type));
        checkRadioButton(Locator.id(type));
        clickAndWait(Ext4Helper.Locators.ext4Button("Submit"));
        goToProjectHome();
    }
}
