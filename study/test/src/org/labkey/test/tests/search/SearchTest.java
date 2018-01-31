/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.test.tests.search;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.labkey.remoteapi.CommandException;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.tests.issues.IssuesTest;
import org.labkey.test.tests.study.StudyTest;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.IssuesHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.Maps;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.SearchHelper;
import org.labkey.test.util.WikiHelper;
import org.labkey.test.util.search.SearchAdminAPIHelper;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.labkey.test.util.PermissionsHelper.MemberType.group;
import static org.labkey.test.util.SearchHelper.getUnsearchableValue;

public abstract class SearchTest extends StudyTest
{
    private final SearchHelper _searchHelper = new SearchHelper(this);
    
    private static final String FOLDER_A = "Folder Apple";
    private static final String FOLDER_B = "Folder Banana"; // Folder move destination
    private static final String FOLDER_C = "Folder Cherry"; // Folder rename name.
    private static final String GROUP_NAME = "Test Group";
    private static final String USER1 = "user1_searchtest@search.test";

    private static final String WIKI_NAME = "Brie";
    private static final String WIKI_TITLE = "Roquefort";
    private static final String WIKI_CONTENT = "Stilton";

    private static final String ISSUE_TITLE = "Sedimentary";
    private static final String ISSUE_BODY = "Igneous";

    private static final String MESSAGE_TITLE = "King";
    private static final String MESSAGE_BODY = "Queen";

    private String FOLDER_NAME = FOLDER_A;
    private static final String GRID_VIEW_NAME = "DRT Eligibility Query";
    private static final String REPORT_NAME = "TestReport";

    private PortalHelper portalHelper = new PortalHelper(this);

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("search");
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    protected String getFolderName()
    {
        return FOLDER_NAME;
    }

    @Override
    protected String getProjectName()
    {
        return "SearchTest Project";// + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    }

    public abstract SearchAdminAPIHelper.DirectoryType directoryType();

    @BeforeClass
    public static void setup()
    {
        SearchTest initTest = (SearchTest)getCurrentTest();
        initTest.doSetup();
    }

    private void doSetup()
    {
        SearchAdminAPIHelper.pauseCrawler(getDriver()); //necessary for the alternate ID testing
        _searchHelper.initialize();
        enableEmailRecorder();
    }

    @Test
    public void testSearch()
    {
        SearchAdminAPIHelper.setDirectoryType(directoryType(), getDriver());
        doCreateSteps();
        doVerifySteps();
    }

    protected void doCreateSteps()
    {
        // TODO: move these out someday into separate tests, like DataClassSearchTest
        addSearchableStudy(); // Must come first;  Creates project.
        addSearchableLists();
        addSearchableContainers();
        //addSearchableReports(); // Reports not currently indexed.
        addSearchableWiki();
        addSearchableIssues();
        //addSearchableMessages();
        addSearchableFiles();
    }

    private static final String fullySearchableList = "List1";       //index both number and text columns
    private static final String textOnlySearchableList = "List2";    //index text columns only
    private static final String metaOnlySearchable = "MetaDataSet";  //index metadata only
    private static final String customizedIndexingList =  "CustomIndexing";  //index one text column but not another
    private static final String listToDelete = "List To Delete";
    private static final String listIndexAsWhole = "Indexed as one doc";

    @LogMethod
    private void addSearchableLists()
    {
        clickTab("Overview");
        portalHelper.addWebPart("Lists");
        _listHelper.importListArchive(FOLDER_A, TestFileUtils.getSampleData("lists/searchTest.lists.zip"));

        clickAndWait(Locator.linkWithText(listToDelete));
        _listHelper.deleteList();

        _searchHelper.enqueueSearchItem("BoarQPine");
        _searchHelper.enqueueSearchItem("Panda", Locator.bodyLinkContainingText("List " + fullySearchableList));
        _searchHelper.enqueueSearchItem("2003-01-02", Locator.bodyLinkContainingText("List " + fullySearchableList));
        _searchHelper.enqueueSearchItem("12345", Locator.bodyLinkContainingText("List " + fullySearchableList));  //Issue 15419
        _searchHelper.enqueueSearchItem("Owlbear", Locator.bodyLinkContainingText("List " + textOnlySearchableList));
        _searchHelper.enqueueSearchItem("54321");
        _searchHelper.enqueueSearchItem(metaOnlySearchable, Locator.bodyLinkContainingText("List " + metaOnlySearchable));
        _searchHelper.enqueueSearchItem("Turtleduck", Locator.bodyLinkContainingText("List " + metaOnlySearchable)); //this phrase is present in the metadata-only file
        _searchHelper.enqueueSearchItem("Cat", Locator.bodyLinkContainingText("List " + customizedIndexingList));
        _searchHelper.enqueueSearchItem("Garfield");
    }

    protected void doVerifySteps()
    {
        _searchHelper.verifySearchResults("/" + getProjectName() + "/" + getFolderName(), false);
        _containerHelper.renameFolder(getProjectName(), getFolderName(), FOLDER_C, true);
        FOLDER_NAME = FOLDER_C;
        _searchHelper.verifySearchResults("/" + getProjectName() + "/" + getFolderName(), false);
        try
        {
            _containerHelper.moveFolder(getProjectName(), getFolderName(), getProjectName() + "/" + FOLDER_B, true);
        }
        catch (CommandException fail)
        {
            throw new RuntimeException(fail);
        }
        alterListsAndReSearch();

        _containerHelper.deleteProject(getProjectName());
        goToHome(); // Need to leave deleted project
        _searchHelper.verifyNoSearchResults();
    }

    private void alterListsAndReSearch()
    {
        log("Verifying list index updated on row insertion.");
        clickFolder(FOLDER_C);
        clickAndWait(Locator.linkWithText(listIndexAsWhole));
        HashMap<String, String> data = new HashMap<>();
        String newAnimal = "Zebra Seal";
        data.put("Animal", newAnimal);
        _listHelper.insertNewRow(data);
        _searchHelper.enqueueSearchItem(newAnimal, Locator.linkContainingText(listIndexAsWhole));
        goBack();
        _searchHelper.verifySearchResults("/" + getProjectName() + "/" + FOLDER_B + "/" + getFolderName(), false);

        // Test case for 20109 Regression after migration to hard tables, updating a row didn't update the index
        log("Verifying list index updated on row update.");
        clickFolder(FOLDER_C);
        clickAndWait(Locator.linkWithText(fullySearchableList));
        String updateAnimal = "BearCatThing";
        data.clear();
        data.put("Name", updateAnimal);
        _listHelper.updateRow(1, data); // Change the "Panda" row
        _searchHelper.enqueueSearchItem("Panda"); // Search for Panda should now return no results.
        _searchHelper.enqueueSearchItem(updateAnimal, Locator.linkContainingText(fullySearchableList));
        goBack();
        _searchHelper.verifySearchResults("/" + getProjectName() + "/" + FOLDER_B + "/" + getFolderName(), false);

    }

    public void runApiTests() throws Exception
    {
        /* No API tests */
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _containerHelper.deleteProject(getProjectName(), false);
    }

    @LogMethod
    private void addSearchableContainers()
    {
        clickProject(getProjectName());
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), FOLDER_B, "None", null);

        //TODO: Fix test to handle searching for folder after move
        //_searchHelper.enqueueSearchItem("Banana", Locator.linkWithText("Folder -- " + FOLDER_B));
    }

    @LogMethod
    private void addSearchableStudy()
    {
        importStudy();
        startSpecimenImport(2);

        waitForPipelineJobsToComplete(2, "study import", false);

        _searchHelper.enqueueSearchItem("999320016", Locator.linkContainingText("999320016"));
        _searchHelper.enqueueSearchItem("Urinalysis", Locator.linkContainingText("URF-1"),
                                                     Locator.linkContainingText("URF-2"),
                                                     Locator.linkContainingText("URS-1"));
    }

    @LogMethod
    private void addSearchableReports()
    {
        clickFolder(FOLDER_A);
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));

        _extHelper.clickMenuButton("Views", "Create", "Crosstab View");
        selectOptionByValue(Locator.name("rowField"),  "DEMsex");
        selectOptionByValue(Locator.name("colField"), "DEMsexor");
        selectOptionByValue(Locator.name("statField"), "MouseId");
        clickButton("Submit");

        String[] row3 = new String[] {"Male", "2", "9", "3", "14"};
        assertTableRowsEqual("report", 3, new String[][] {row3});

        setFormElement(Locator.name("label"), REPORT_NAME);
        clickButton("Save");

        // create new grid view report:
        goToManageViews();
        _extHelper.clickExtMenuButton(false, Locator.linkContainingText("Add Report"), "Grid View");
        setFormElement(Locator.id("label"), GRID_VIEW_NAME);
        selectOptionByText(Locator.name("params"), "ECI-1 (ECI-1: Eligibility Criteria)");
        clickButton("Create View");

        // create new external report
        clickFolder(FOLDER_A);
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        _extHelper.clickMenuButton("Views", "Create", "Advanced View");
        selectOptionByText(Locator.name("queryName"), "DEM-1 (DEM-1: Demographics)");
        String java = System.getProperty("java.home") + "/bin/java";
        setFormElement(Locator.name("program"), java);
        setFormElement(Locator.name("arguments"), "-cp " + new File(TestFileUtils.getTestBuildDir(), "classes") + " org.labkey.test.util.Echo ${DATA_FILE} ${REPORT_FILE}");
        clickButton("Submit");
        assertTextPresent("Female");
        setFormElement(Locator.name("program"), java);
        setFormElement(Locator.name("arguments"), "-cp " + new File(TestFileUtils.getTestBuildDir(), "classes") + " org.labkey.test.util.Echo ${DATA_FILE}");
        selectOptionByValue(Locator.name("fileExtension"), "tsv");
        clickButton("Submit");
        assertTextPresent("Female");
        setFormElement(Locator.name("label"), "tsv");
        selectOptionByText(Locator.name("showWithDataset"), "DEM-1: Demographics");
        clickButton("Save");
    }

    @LogMethod
    private void addSearchableWiki()
    {
        WikiHelper _wikiHelper = new WikiHelper(this);
        File attachedFile = TestFileUtils.getSampleData("fileTypes/sample.txt");
        
        clickFolder(getFolderName());
        portalHelper.addWebPart("Wiki");
        _wikiHelper.createWikiPage(WIKI_NAME, "RADEOX", WIKI_TITLE, WIKI_CONTENT, attachedFile);
        waitForElement(Locator.linkContainingText(attachedFile.getName()));
        portalHelper.addWebPart("Wiki");
        //Issue 9454: Don't index option for wiki page
        _wikiHelper.createWikiPage(WIKI_NAME + " " + getUnsearchableValue(), "RADEOX", WIKI_TITLE + " " + getUnsearchableValue(), WIKI_CONTENT + " " + getUnsearchableValue(), false, null, true);

        _searchHelper.enqueueSearchItem(WIKI_NAME, Locator.linkWithText(WIKI_TITLE));
        _searchHelper.enqueueSearchItem(WIKI_TITLE, Locator.linkWithText(WIKI_TITLE));
        _searchHelper.enqueueSearchItem(WIKI_CONTENT, Locator.linkWithText(WIKI_TITLE));
        _searchHelper.enqueueSearchItem("Sample", Locator.linkWithText(String.format("\"%s\" attached to page \"%s\"", attachedFile.getName(), WIKI_TITLE))); // some text from attached file
    }

    @LogMethod
    private void addSearchableIssues()
    {
        ApiPermissionsHelper apiPermissionsHelper = new ApiPermissionsHelper(this);
        apiPermissionsHelper.createPermissionsGroup(GROUP_NAME, USER1);
        apiPermissionsHelper.addMemberToRole(GROUP_NAME, "Editor", group, getProjectName());
        clickFolder(getFolderName());

        IssuesHelper issuesHelper = new IssuesHelper(this);
        issuesHelper.createNewIssuesList("issues", _containerHelper);
        goToModule("Issues");
        issuesHelper.goToAdmin();
        issuesHelper.setIssueAssignmentList(null);
        clickButton("Save");

        // Add Area
        IssuesTest.addLookupValues(this, "issues", "area", Collections.singleton("Area51"));

        // Add Type
        IssuesTest.addLookupValues(this, "issues", "type", Collections.singleton("UFO"));

        // Create new issue.
        goToModule("Issues");
        File file = TestFileUtils.getSampleData("fileTypes/tsv_sample.tsv");
        issuesHelper.addIssue(ISSUE_TITLE, _userHelper.getDisplayNameForEmail(USER1),
                Maps.of("type", "UFO",
                        "area", "Area51",
                        "priority", "1",
                        "comment", ISSUE_BODY),
                file);

        _searchHelper.enqueueSearchItem(ISSUE_TITLE, Locator.linkContainingText(ISSUE_TITLE));
        _searchHelper.enqueueSearchItem(ISSUE_BODY, Locator.linkContainingText(ISSUE_TITLE));
        _searchHelper.enqueueSearchItem(_userHelper.getDisplayNameForEmail(USER1), Locator.linkContainingText(ISSUE_TITLE));
        _searchHelper.enqueueSearchItem("Area51", Locator.linkContainingText(ISSUE_TITLE));
        _searchHelper.enqueueSearchItem("UFO", Locator.linkContainingText(ISSUE_TITLE));
        // TODO: 9583: Index issue attachments
        //_searchHelper.enqueueSearchItem("Background", Locator.linkWithText(String.format("\"%s\" attached to issue \"%s\"", file.getName(), ISSUE_TITLE))); // some text from attached file
    }

    @LogMethod
    private void addSearchableMessages()
    {
        clickFolder(getFolderName());
        portalHelper.addWebPart("Messages");
        portalHelper.clickWebpartMenuItem("Messages", "New");
        setFormElement(Locator.name("title"), MESSAGE_TITLE);
        setFormElement(Locator.id("body"), MESSAGE_BODY);
        click(Locator.linkWithText("Attach a file"));
        File file = TestFileUtils.getSampleData("dataloading/excel/fruits.tsv");
        setFormElement(Locator.name("formFiles[0]"), file);
        clickButton("Submit");

        _searchHelper.enqueueSearchItem(MESSAGE_TITLE, Locator.linkContainingText(MESSAGE_TITLE));
        _searchHelper.enqueueSearchItem(MESSAGE_BODY, Locator.linkContainingText(MESSAGE_TITLE));
        _searchHelper.enqueueSearchItem("persimmon", Locator.linkContainingText("\"fruits.tsv\" attached to message \"" + MESSAGE_TITLE + "\"")); // some text from attached file
    }

    @LogMethod
    private void addSearchableFiles()
    {
        clickFolder(getFolderName());
        goToModule("FileContent");
        File htmlFile = TestFileUtils.getSampleData("security/InlineFile.html");
        _fileBrowserHelper.uploadFile(htmlFile);
        File MLfile = TestFileUtils.getSampleData("mzxml/test_nocompression.mzXML");
        _fileBrowserHelper.uploadFile(MLfile);
        File docFile = TestFileUtils.getSampleData("fileTypes/docx_sample.docx");
        _fileBrowserHelper.uploadFile(docFile);
        File pdfFile = TestFileUtils.getSampleData("fileTypes/pdf_sample.pdf");
        _fileBrowserHelper.uploadFile(pdfFile);

        _searchHelper.enqueueSearchItem("antidisestablishmentarianism", true, Locator.linkWithText(htmlFile.getName()));
        _searchHelper.enqueueSearchItem("ThermoFinnigan", true, Locator.linkWithText(MLfile.getName()));
        _searchHelper.enqueueSearchItem("acyclic", true, Locator.linkWithText(pdfFile.getName()));
        _searchHelper.enqueueSearchItem("Audience", true, Locator.linkWithText(docFile.getName()));
    }

    @Override @Ignore
    public void testSteps() throws Exception
    {
        // Mask parent test
    }
}
