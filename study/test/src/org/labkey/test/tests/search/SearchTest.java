/*
 * Copyright (c) 2016-2019 LabKey Corporation
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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.SimpleGetCommand;
import org.labkey.remoteapi.security.GetContainersCommand;
import org.labkey.remoteapi.security.GetContainersResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestProperties;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.pages.core.admin.logger.ManagerPage;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.tests.issues.IssuesTest;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.IssuesHelper;
import org.labkey.test.util.Log4jUtils;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.Maps;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.SearchHelper;
import org.labkey.test.util.WikiHelper;
import org.labkey.test.util.search.SearchAdminAPIHelper;
import org.labkey.test.util.search.SearchResultsQueue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.test.util.PermissionsHelper.MemberType.group;
import static org.labkey.test.util.SearchHelper.getUnsearchableValue;

@BaseWebDriverTest.ClassTimeout(minutes = 30)
public abstract class SearchTest extends StudyBaseTest
{
    private static final SearchResultsQueue SEARCH_RESULTS_QUEUE = new SearchResultsQueue();
    private final SearchHelper _searchHelper = new SearchHelper(this, SEARCH_RESULTS_QUEUE).setMaxTries(6);
    
    private static final String FOLDER_A = "Folder Apple";
    private static final String FOLDER_B = "Folder Banana"; // Folder move destination
    private static final String FOLDER_C = "Folder Cherry"; // Folder rename name.
    private static final String FOLDER_D = "Subfolder Date";
    private static final String FOLDER_E = "Subfolder Eggfruit";
    private static final String GROUP_NAME = "Test Group";
    private static final String USER1 = "geologist@search.test";

    private static final String WIKI_NAME = "Brie";
    private static final String WIKI_TITLE = "Roquefort";
    private static final String WIKI_CONTENT = "Stilton";

    private static final String ISSUE_TITLE = "Sedimentary";
    private static final String ISSUE_BODY = "Igneous";

    private String FOLDER_NAME = FOLDER_A;

    private final PortalHelper portalHelper = new PortalHelper(this);

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

    @Override
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
    public static void setup() throws IOException, CommandException
    {
        SearchTest initTest = (SearchTest)getCurrentTest();
        initTest.doSetup();
    }

    @LogMethod
    private void doSetup() throws IOException, CommandException
    {
        if (TestProperties.isTestRunningOnTeamCity())
        {
            deleteConflictingProjects();
        }
        Log4jUtils.setLogLevel("org.labkey.search", ManagerPage.LoggingLevel.DEBUG);
        Log4jUtils.setLogLevel("org.labkey.wiki", ManagerPage.LoggingLevel.DEBUG);
        _searchHelper.initialize();
        SearchAdminAPIHelper.startCrawler(getDriver());
        enableEmailRecorder();
    }

    @LogMethod
    private void deleteConflictingProjects() throws IOException, CommandException
    {
        Set<String> projects = new HashSet<>();
        projects.addAll(getSearchResultsProjects("Owlbear")); // List
        projects.addAll(getSearchResultsProjects("Urinalysis")); // Study
        projects.addAll(getSearchResultsProjects("999320016")); // Specimens
        projects.addAll(getSearchResultsProjects(WIKI_NAME)); // Wiki
        projects.addAll(getSearchResultsProjects(ISSUE_TITLE)); // Issues
        projects.addAll(getSearchResultsProjects("acyclic")); // Files

        for (String project : projects)
        {
            _containerHelper.deleteProject(project, false);
        }
    }

    private Set<String> getSearchResultsProjects(String q) throws IOException, CommandException
    {
        Connection connection = createDefaultConnection();

        SimpleGetCommand command = new SimpleGetCommand("search", "json");
        command.setParameters(Map.of("q", q, "scope", "All"));
        CommandResponse searchResponse = command.execute(connection, "/");
        List<Map<String, Object>> hits = searchResponse.getProperty("hits");
        Set<String> containerIds = new HashSet<>();
        for (Map<String, Object> hit : hits)
        {
            containerIds.add((String) hit.get("container"));
        }
        Set<String> projects = new HashSet<>();

        for (String containerId : containerIds)
        {
            GetContainersResponse response = new GetContainersCommand().execute(connection, containerId);
            String path = response.getProperty("path");
            path = path.substring(path.indexOf("/") + 1); // Strip leading '/'
            if (!path.isBlank())
            {
                projects.add(path.split("/", 2)[0]); // Get project from path
            }
        }
        return projects;
    }

    @Test
    public void testSearch()
    {
        SearchAdminAPIHelper.setDirectoryType(directoryType(), getDriver());
        doCreateSteps();
        doVerifySteps();
    }

    @Override
    protected void doCreateSteps()
    {
        // TODO: move these out someday into separate tests, like DataClassSearchTest
        addSearchableStudy(); // Must come first;  Creates project.
        addSearchableLists();
        addSearchableContainers();
        addSearchableWiki();
        addSearchableIssues();
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
        _searchHelper.enqueueSearchItem("Panda", Locator.linkContainingText("List " + fullySearchableList));
        _searchHelper.enqueueSearchItem("\"2003-01-02\"", Locator.linkContainingText("List " + fullySearchableList));
        _searchHelper.enqueueSearchItem("12345", Locator.linkContainingText("List " + fullySearchableList));  //Issue 15419
        _searchHelper.enqueueSearchItem("Owlbear", Locator.linkContainingText("List " + textOnlySearchableList));
        _searchHelper.enqueueSearchItem("54321");
        _searchHelper.enqueueSearchItem(metaOnlySearchable, Locator.linkContainingText("List " + metaOnlySearchable));
        _searchHelper.enqueueSearchItem("Turtleduck", Locator.linkContainingText("List " + metaOnlySearchable)); //this phrase is present in the metadata-only file
        _searchHelper.enqueueSearchItem("Cat", Locator.linkContainingText("List " + customizedIndexingList));
        _searchHelper.enqueueSearchItem("Garfield");
    }

    @Override
    @LogMethod
    protected void doVerifySteps()
    {
        _searchHelper.verifySearchResults("/" + getProjectName() + "/" + getFolderName());
        testAdvancedSearchUI();
        renameFolderAndReSearch();
        moveFolderAlterListsAndReSearch();
        deleteFolderAndVerifyNoResults();
    }

    private void testAdvancedSearchUI()
    {
        final String searchTerm = "Sample";

        goToProjectHome();
        _searchHelper.searchFor(searchTerm, false);  //already waited above

        expandAdvancedOptions();

        waitAndClick(Locator.radioButtonByNameAndValue("scope", "Project"));
        _searchHelper.searchFor(searchTerm);
        waitForElement(Locator.tagWithText("div", "Found 6 results"));

        waitAndClick(Locator.radioButtonByNameAndValue("scope", "Folder"));
        _searchHelper.searchFor(searchTerm);
        waitForElement(Locator.tagWithText("div", "Found 0 results"));

        waitAndClick(Locator.radioButtonByNameAndValue("scope", "FolderAndSubfolders"));
        _searchHelper.searchFor(searchTerm);
        waitForElement(Locator.tagWithText("div", "Found 6 results"));

        //Now create subfolders:
        goToProjectHome();
        _containerHelper.createSubfolder(getProjectName(), FOLDER_D);
        _containerHelper.createSubfolder(getProjectName(), FOLDER_E);
        goToProjectHome();
        final String searchTerm2 = "Subfolder";
        _searchHelper.searchFor(searchTerm2, true);

        expandAdvancedOptions();

        waitAndClick(Locator.radioButtonByNameAndValue("scope", "Project"));
        _searchHelper.searchFor(searchTerm2);
        waitForElement(Locator.tagWithText("div", "Found 2 results"));

        waitAndClick(Locator.radioButtonByNameAndValue("scope", "Folder"));
        _searchHelper.searchFor(searchTerm2);
        waitForElement(Locator.tagWithText("div", "Found 0 results"));

        waitAndClick(Locator.radioButtonByNameAndValue("scope", "FolderAndSubfolders"));
        _searchHelper.searchFor(searchTerm2);
        waitForElement(Locator.tagWithText("div", "Found 2 results"));

        //test folder sort:
        waitAndClick(Locator.radioButtonByNameAndValue("sortField", "container"));
        _searchHelper.searchFor(searchTerm2);

        assertTextBefore("Folder -- " + FOLDER_D, "Folder -- " + FOLDER_E);

        waitAndClick(Locator.checkboxByName("invertSort"));
        _searchHelper.searchFor(searchTerm2);
        assertTextBefore("Folder -- " + FOLDER_E, "Folder -- " + FOLDER_D);

        _containerHelper.deleteFolder(getProjectName(), FOLDER_D);
        _containerHelper.deleteFolder(getProjectName(), FOLDER_E);
    }

    private void expandAdvancedOptions()
    {
        waitAndClick(Locator.tagWithText("a", "advanced options"));
        waitForElement(Locator.tag("input").withAttribute("name", "invertSort").notHidden());
    }

    @LogMethod
    private void renameFolderAndReSearch()
    {
        _containerHelper.renameFolder(getProjectName(), getFolderName(), FOLDER_C, true);
        SearchAdminAPIHelper.waitForIndexerBackground();
        FOLDER_NAME = FOLDER_C;
        _searchHelper.verifySearchResults("/" + getProjectName() + "/" + getFolderName(), "searchAfterFolderRename");
    }

    @LogMethod
    private void moveFolderAlterListsAndReSearch()
    {
        try
        {
            _containerHelper.moveFolder(getProjectName(), getFolderName(), getProjectName() + "/" + FOLDER_B, true);
            SearchAdminAPIHelper.waitForIndexerBackground();
        }
        catch (CommandException fail)
        {
            throw new RuntimeException(fail);
        }

        log("Verifying list index updated on row insertion.");
        clickFolder(FOLDER_C);
        clickAndWait(Locator.linkWithText(listIndexAsWhole));
        HashMap<String, String> data = new HashMap<>();
        String newAnimal = "Zebra Seal";
        data.put("Animal", newAnimal);
        _listHelper.insertNewRow(data);
        _searchHelper.enqueueSearchItem(newAnimal, Locator.linkContainingText(listIndexAsWhole));
        goBack();
        _searchHelper.verifySearchResults("/" + getProjectName() + "/" + FOLDER_B + "/" + getFolderName(), "searchAfterMoveAndInsert");

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
        _searchHelper.verifySearchResults("/" + getProjectName() + "/" + FOLDER_B + "/" + getFolderName(), "searchAfterListUpdate");

    }

    @LogMethod
    private void deleteFolderAndVerifyNoResults()
    {
        _containerHelper.deleteProject(getProjectName());
        SearchAdminAPIHelper.waitForIndexerBackground();
        goToHome(); // Need to leave deleted project
        _searchHelper.verifyNoSearchResults();
    }

    @Override
    public void runApiTests()
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

        try
        {
            // Add Area
            IssuesTest.addLookupValues(getCurrentContainerPath(), "issues", "area", Collections.singleton("Area51"));

            // Add Type
            IssuesTest.addLookupValues(getCurrentContainerPath(), "issues", "type", Collections.singleton("UFO"));
        }
        catch (IOException | CommandException e)
        {
            throw new RuntimeException(e);
        }

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
    public void testSteps()
    {
        // Mask parent test
    }

    @AfterClass
    public static void resetLogger()
    {
        Log4jUtils.resetAllLogLevels();

        //Turn crawler off after test is finished
        SearchAdminAPIHelper.pauseCrawler(getCurrentTest().getWrappedDriver());
    }
}
