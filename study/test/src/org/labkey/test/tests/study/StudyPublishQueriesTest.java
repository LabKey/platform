package org.labkey.test.tests.study;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.Daily;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.StudyHelper;
import org.openqa.selenium.WebElement;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

@Category({Daily.class})
public class StudyPublishQueriesTest extends BaseWebDriverTest
{
    public static final String PROJECT_NAME = "StudyPublishQueriesTestProject";
    public static final String USER_DEFINED_QUERY = "MyUserDefinedQuery";
    public static final List<String> SCHEMA_NAMES = Arrays.asList("wiki", "core", "study");

    @BeforeClass
    public static void setupProject()
    {
        StudyPublishQueriesTest init = (StudyPublishQueriesTest) getCurrentTest();
        init.doSetup();
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), "Study");
        _studyHelper.startCreateStudy().createStudy();

        // need at least one visit for the publish study wizard
        _studyHelper.goToManageVisits().goToImportVisitMap();
        setFormElement(Locator.name("content"),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<visitMap xmlns=\"http://labkey.org/study/xml\">\n" +
                "  <visit label=\"Test Visit1\" typeCode=\"X\" sequenceNum=\"1.0\" maxSequenceNum=\"1.0\"/>\n" +
                "  <visit label=\"Test Visit2\" typeCode=\"X\" sequenceNum=\"2.0\" maxSequenceNum=\"2.0\"/>\n" +
                "  <visit label=\"Test Visit3\" typeCode=\"X\" sequenceNum=\"3.0\" maxSequenceNum=\"3.0\"/>\n" +
                "</visitMap>"
        );
        clickButton("Import");

        // create user defined queries of the same name in different schemas
        for (String schemaName : SCHEMA_NAMES)
            createQuery(getProjectName(), USER_DEFINED_QUERY, schemaName, "SELECT 'Test from " + schemaName + " schema'", null, false);
    }

    @Override // TODO delete me
    protected void doCleanup(boolean afterTest)
    {
        super.doCleanup(afterTest);
    }

    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Collections.singletonList("study");
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Test
    public void testPublishStudyWithNoQueries()
    {
        String folderName = "No Queries";
        publishStudy(folderName, false);
        clickFolder(folderName);
        verifyQueriesExist(Collections.emptyList());
        verifyRepublishQuerySelections(folderName);
    }

    @Test
    public void testPublishStudyWithAllQueries()
    {
        String folderName = "All Queries";
        publishStudy(folderName, true);
        clickFolder(folderName);
        verifyQueriesExist(SCHEMA_NAMES);
        verifyRepublishQuerySelections(folderName, "wiki", "core", "study");
    }

    @Test
    public void testPublishStudyWithSubsetOfQueries()
    {
        String folderName = "Subset Queries";
        publishStudy(folderName, false, "wiki||" + USER_DEFINED_QUERY, "study||" + USER_DEFINED_QUERY);
        clickFolder(folderName);
        verifyQueriesExist(Arrays.asList("wiki", "study"));
        verifyRepublishQuerySelections(folderName, "wiki", "study");
    }

    private void verifyQueriesExist(List<String> expectedSchemaNames)
    {
        for (String schemaName : SCHEMA_NAMES)
        {
            goToSchemaBrowser();

            if (expectedSchemaNames.contains(schemaName))
            {
                DataRegionTable table = viewQueryData(schemaName, USER_DEFINED_QUERY);
                Assert.assertEquals("Expected one row for existing queries", 1, table.getDataRowCount());
                Assert.assertEquals("Expected one row to have expected value", "Test from " + schemaName + " schema", table.getDataAsText(0, 0));
            }
            else
            {
                selectSchema(schemaName);
                Locator.XPathLocator queryLoc = Locator.tagWithClass("span", "labkey-link").withText(USER_DEFINED_QUERY);
                assertElementNotPresent(queryLoc);
            }
        }
    }

    private void verifyRepublishQuerySelections(String folderName, String... schemaNames)
    {
        goToProjectHome();
        goToSchemaBrowser();
        DataRegionTable table = viewQueryData("study", "StudySnapshot");
        int rowIndex = table.getRowIndex("Destination", folderName);
        click(Locator.linkWithText("Republish").index(rowIndex));
        _extHelper.waitForExtDialog("Republish Study");

        _studyHelper.advanceThroughPublishStudyWizard(Arrays.asList(
                StudyHelper.Panel.studyGeneralSetup,
                StudyHelper.Panel.studyWizardParticipantList,
                StudyHelper.Panel.studyWizardDatasetList,
                StudyHelper.Panel.studyWizardVisitList,
                StudyHelper.Panel.studyObjects,
                StudyHelper.Panel.studyWizardListList
        ));

        verifyPublishWizardSelectedCheckboxes(schemaNames);
    }

    public void verifyPublishWizardSelectedCheckboxes(String... expectedSchemaNames)
    {
        Locator.CssLocator gridLoc = Locator.css("div.studyWizardQueryList");
        WebElement columnHeader = gridLoc.append(Locator.css("tr.x-grid3-hd-row > td")).withText("Schema Name").findElement(getDriver());
        Integer colIndex = getElementIndex(columnHeader);
        Locator selectedColLoc = gridLoc.append(Locator.css("div.x-grid3-row-selected div.x-grid3-col-" + colIndex));
        List<WebElement> selectedRows = selectedColLoc.findElements(getDriver());
        Set<String> selectedSchemaNames = new HashSet<>(getTexts(selectedRows));
        assertEquals("Republish Study has wrong query checkboxes checked", new HashSet<>(Arrays.asList(expectedSchemaNames)), selectedSchemaNames);
    }

    private void publishStudy(String studyName, boolean selectAllQueries, String... queryKeys)
    {
        goToManageStudy();
        clickButton("Publish Study", 0);
        _extHelper.waitForExtDialog("Publish Study");

        // Need to handle republish panel (Previous Settings)
        if (isElementPresent(Locator.xpath("//div[@class = 'labkey-nav-page-header'][text() = 'Previous Settings']")))
        {
            checkCheckbox(Locator.radioButtonByNameAndValue("publishType", "publish"));
            clickButton("Next", 0);
        }

        // General Setup
        waitForElement(Locator.xpath("//div[@class = 'labkey-nav-page-header'][text() = 'General Setup']"));
        setFormElement(Locator.name("studyName"), studyName);
        clickButton("Next", 0);

        _studyHelper.advanceThroughPublishStudyWizard(Arrays.asList(
                StudyHelper.Panel.studyWizardParticipantList,
                StudyHelper.Panel.studyWizardDatasetList
        ));
        // visits is the only one we need to select all from in these cases
        _studyHelper.advanceThroughPublishStudyWizard(Arrays.asList(
                StudyHelper.Panel.studyWizardVisitList
        ), true);
        _studyHelper.advanceThroughPublishStudyWizard(Arrays.asList(
                StudyHelper.Panel.studyObjects,
                StudyHelper.Panel.studyWizardListList
        ));

        // Queries
        waitForElement(Locator.xpath("//div[@class = 'labkey-nav-page-header'][text() = 'Queries']"));
        waitForElement(Locator.css(".studyWizardQueryList"));
        if (selectAllQueries)
            click(Locator.css(".studyWizardQueryList .x-grid3-hd-checker  div"));
        else
        {
            for (String queryKey : queryKeys)
                _extHelper.selectExtGridItem("key", queryKey, -1, "studyWizardQueryList", true);
        }
        clickButton("Next", 0);

        _studyHelper.advanceThroughPublishStudyWizard(Arrays.asList(
                StudyHelper.Panel.studyWizardViewList,
                StudyHelper.Panel.studyWizardReportList,
                StudyHelper.Panel.folderObjects,
                StudyHelper.Panel.studyWizardPublishOptionsList
        ));

        waitForRunningPipelineJobs(WAIT_FOR_PAGE);
    }
}
