/*
 * Copyright (c) 2010-2015 LabKey Corporation
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

import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyB;
import org.labkey.test.tests.IssuesTest;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.SearchHelper;
import org.labkey.test.util.WikiHelper;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

@Category({DailyB.class})
public class SearchTest extends StudyTest
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

    @Override
    protected String getFolderName()
    {
        return FOLDER_NAME;
    }

    protected void doCreateSteps()
    {
        _searchHelper.deleteIndex();
        addSearchableStudy(); // Must come first;  Creates project.
        addSearchableLists();
        addSearchableContainers();
        //addSearchableReports(); // Reports not currently indexed.
        addSearchableWiki();
        addSearchableIssues();
        //addSearchableMessages();
        addSearchableFiles();
    }

    String fullySearchableList = "List1";       //index both number and text colums
    String textOnlySearchableList = "List2";    //index text columns only
    String metaOnlySearchable = "MetaDataSet";  //index metadata only
    String customizedIndexingList =  "CustomIndexing";  //index one text column but not another
    String listToDelete = "List To Delete";
    String listIndexAsWhole = "Indexed as one doc";

    private void addSearchableLists()
    {
        clickTab("Overview");
        portalHelper.addWebPart("Lists");
        _listHelper.importListArchive(FOLDER_A, TestFileUtils.getSampleData("lists/searchTest.lists.zip"));

        clickAndWait(Locator.linkWithText(listToDelete));
        _listHelper.deleteList();

        _searchHelper.enqueueSearchItem("BoarQPine");
        _searchHelper.enqueueSearchItem("Panda", Locator.bodyLinkWithText("List " + fullySearchableList));
        _searchHelper.enqueueSearchItem("2003-01-02", Locator.bodyLinkWithText("List " + fullySearchableList));
        _searchHelper.enqueueSearchItem("12345", Locator.bodyLinkWithText("List " + fullySearchableList));  //Issue 15419
        _searchHelper.enqueueSearchItem("Owlbear", Locator.bodyLinkWithText("List " + textOnlySearchableList));
        _searchHelper.enqueueSearchItem("54321");
        _searchHelper.enqueueSearchItem(metaOnlySearchable, Locator.bodyLinkWithText("List " + metaOnlySearchable));
        _searchHelper.enqueueSearchItem("Turtleduck", Locator.bodyLinkWithText("List " + metaOnlySearchable)); //this phrase is present in the metadata-only file
        _searchHelper.enqueueSearchItem("Cat", Locator.bodyLinkWithText("List " + customizedIndexingList));
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

        verifySyntaxErrorMessages();
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
    
    private void verifySyntaxErrorMessages()
    {
        _searchHelper.searchFor("age()", false);
        checkSyntaxErrorMessage("Error: Can't parse 'age()': Problem character is highlighted", "These characters have special meaning within search queries:", "You can escape special characters using \\ before the character or you can enclose the query string in double quotes.", "For more information, visit the search syntax documentation.");
        _searchHelper.searchFor("incomplete(", false);
        checkSyntaxErrorMessage("Error: Can't parse 'incomplete(': Query string is incomplete", "These characters have special meaning within search queries:");
        _searchHelper.searchFor("this AND OR", false);
        checkSyntaxErrorMessage("Error: Can't parse 'this AND OR': Problem character is highlighted", "Boolean operators AND, OR, and NOT have special meaning within search queries");
    }

    private void checkSyntaxErrorMessage(String... expectedPhrases)
    {
        String errorText = getText(Locator.css("#searchResults + table"));
        // We want our nice, custom error messages to appear
        for (String phrase : expectedPhrases)
        {
            assertTrue("Did not find expected error message: " + phrase, errorText.contains(phrase));
        }

        // Various phrases that appear in the standard Lucene system error message
        assertTextNotPresent("Cannot parse", "encountered", "Was expecting", "<NOT>", "<OR>", "<AND>", "<EOF>");
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
        if (afterTest)
        {
            _searchHelper.verifyNoSearchResults();
        }
    }

    private void addSearchableContainers()
    {
        clickProject(getProjectName());
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), FOLDER_B, "None", null);
    }

    private void addSearchableStudy()
    {
        super.doCreateSteps(); // import study and specimens

        _searchHelper.enqueueSearchItem("999320016", Locator.linkContainingText("999320016"));
        _searchHelper.enqueueSearchItem("Urinalysis", Locator.linkContainingText("URF-1"),
                                                     Locator.linkContainingText("URF-2"),
                                                     Locator.linkContainingText("URS-1"));
    }

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
        setFormElement(Locator.name("arguments"), "-cp " + TestFileUtils.getLabKeyRoot() + "/server/test/build/classes org.labkey.test.util.Echo ${DATA_FILE} ${REPORT_FILE}");
        clickButton("Submit");
        assertTextPresent("Female");
        setFormElement(Locator.name("program"), java);
        setFormElement(Locator.name("arguments"), "-cp " + TestFileUtils.getLabKeyRoot() + "/server/test/build/classes org.labkey.test.util.Echo ${DATA_FILE}");
        selectOptionByValue(Locator.name("fileExtension"), "tsv");
        clickButton("Submit");
        assertTextPresent("Female");
        setFormElement(Locator.name("label"), "tsv");
        selectOptionByText(Locator.name("showWithDataset"), "DEM-1: Demographics");
        clickButton("Save");
    }

    private void addSearchableWiki()
    {
        WikiHelper _wikiHelper = new WikiHelper(this);
        File attachedFile = TestFileUtils.getSampleData("fileTypes/sample.txt");
        
        clickFolder(getFolderName());
        portalHelper.addWebPart("Wiki");
        _wikiHelper.createWikiPage(WIKI_NAME, "RADEOX", WIKI_TITLE, WIKI_CONTENT, attachedFile);
        portalHelper.addWebPart("Wiki");
        //Issue 9454: Don't index option for wiki page
        _wikiHelper.createWikiPage(WIKI_NAME + "UNSEARCHABLE", "RADEOX", WIKI_TITLE, WIKI_CONTENT, false, null, true);

        _searchHelper.enqueueSearchItem(WIKI_NAME, Locator.linkWithText(WIKI_TITLE));
        _searchHelper.enqueueSearchItem(WIKI_NAME + "UNSEARCHABLE");
        _searchHelper.enqueueSearchItem(WIKI_TITLE, Locator.linkWithText(WIKI_TITLE));
        _searchHelper.enqueueSearchItem(WIKI_CONTENT, Locator.linkWithText(WIKI_TITLE));
        _searchHelper.enqueueSearchItem("Sample", Locator.linkWithText(String.format("\"%s\" attached to page \"%s\"", attachedFile.getName(), WIKI_TITLE))); // some text from attached file
    }

    private void addSearchableIssues()
    {
        _permissionsHelper.createPermissionsGroup(GROUP_NAME, USER1);
        _securityHelper.setProjectPerm(GROUP_NAME, "Editor");
        clickButton("Save and Finish");
        clickFolder(getFolderName());
        portalHelper.addWebPart("Issues Summary");

        // Setup issues options.
        clickAndWait(Locator.linkWithText("Issues Summary"));
        clickButton("Admin");

        // Add Area
        IssuesTest.addKeywordsAndVerify(this, "area", "Area", "Area51");

        // Add Type
        IssuesTest.addKeywordsAndVerify(this, "type", "Type", "UFO");

        // Create new issue.
        clickButton("Back to Issues");
        clickButton("New Issue");
        setFormElement(Locator.name("title"), ISSUE_TITLE);
        selectOptionByText(Locator.name("type"), "UFO");
        selectOptionByText(Locator.name("area"), "Area51");
        selectOptionByText(Locator.name("priority"), "1");
        setFormElement(Locator.id("comment"), ISSUE_BODY);
        selectOptionByText(Locator.name("assignedTo"), displayNameFromEmail(USER1));
        click(Locator.linkWithText("Attach a file"));
        File file = new File(TestFileUtils.getLabKeyRoot() + "/common.properties");
        setFormElement(Locator.name("formFiles[00]"), file);
        clickButton("Save");

        _searchHelper.enqueueSearchItem(ISSUE_TITLE, Locator.linkContainingText(ISSUE_TITLE));
        _searchHelper.enqueueSearchItem(ISSUE_BODY, Locator.linkContainingText(ISSUE_TITLE));
        _searchHelper.enqueueSearchItem(displayNameFromEmail(USER1), Locator.linkContainingText(ISSUE_TITLE));
        _searchHelper.enqueueSearchItem("Area51", Locator.linkContainingText(ISSUE_TITLE));
        _searchHelper.enqueueSearchItem("UFO", Locator.linkContainingText(ISSUE_TITLE));
        //_searchHelper.enqueueSearchItem("Override", Locator.linkWithText("\"common.properties\" attached to issue \"" + ISSUE_TITLE + "\"")); // some text from attached file
    }

    private void addSearchableMessages()
    {
        clickFolder(getFolderName());
        portalHelper.addWebPart("Messages");
        portalHelper.clickWebpartMenuItem("Messages", "New");
        setFormElement(Locator.name("title"), MESSAGE_TITLE);
        setFormElement(Locator.id("body"), MESSAGE_BODY);
        click(Locator.linkWithText("Attach a file"));
        File file = new File(TestFileUtils.getLabKeyRoot() + "/sampledata/dataloading/excel/fruits.tsv");
        setFormElement(Locator.name("formFiles[0]"), file);
        clickButton("Submit");

        _searchHelper.enqueueSearchItem(MESSAGE_TITLE, Locator.linkContainingText(MESSAGE_TITLE));
        _searchHelper.enqueueSearchItem(MESSAGE_BODY, Locator.linkContainingText(MESSAGE_TITLE));
        _searchHelper.enqueueSearchItem("persimmon", Locator.linkContainingText("\"fruits.tsv\" attached to message \"" + MESSAGE_TITLE + "\"")); // some text from attached file
    }

    private void addSearchableFiles()
    {
        clickFolder(getFolderName());
        goToModule("FileContent");
        File file = new File(TestFileUtils.getLabKeyRoot() + "/sampledata/security", "InlineFile.html");
        _fileBrowserHelper.uploadFile(file);
        File MLfile = new File(TestFileUtils.getLabKeyRoot() + "/sampledata/mzxml", "test_nocompression.mzXML");
        _fileBrowserHelper.uploadFile(MLfile);

        _searchHelper.enqueueSearchItem("antidisestablishmentarianism", true, Locator.linkWithText(file.getName()));
        _searchHelper.enqueueSearchItem("ThermoFinnigan", true, Locator.linkWithText(MLfile.getName()));
    }
}
