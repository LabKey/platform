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
package org.labkey.test.tests.search;

import org.apache.http.HttpStatus;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.PostCommand;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Search;
import org.labkey.test.tests.study.StudyTest;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.SearchHelper;
import org.labkey.test.util.search.SearchAdminAPIHelper;
import org.openqa.selenium.NoSuchElementException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Category({Search.class, DailyB.class})
public class DataClassSearchTest extends StudyTest
{
    public SearchAdminAPIHelper.DirectoryType directoryType()
    {
        return SearchAdminAPIHelper.DirectoryType.Default;
    }

    private final SearchHelper _searchHelper = new SearchHelper(this);

    private static final String DATA_CLASS_DOMAIN_1 = "DataClassSearchTestDomain1";
    private static final String DATA_CLASS_DOMAIN_2 = "DataClassSearchTestDomain2";
    private static final String DATA_CLASS_SCHEMA = "exp.data";
    private static final String DATA_CLASS_DOMAIN_DESCRIPTION = "DataClassSearchTest description";
    private static final String DATA_CLASS_DOMAIN_EXPRESSION = "OPT-${genId}";
    private static final String DATA_CLASS_1_NAME_1 = "DataClass1";
    private static final String DATA_CLASS_1_DESCRIPTION_1 = "DataClass1_description_for_testing";
    private static final String DATA_CLASS_1_ALIAS_1 = "testAliasForDataClassSearchIndexing1";
    private static final String DATA_CLASS_1_COMMENT_1 = "Some_comment_about_the_data_class";
    private static final String DATA_CLASS_1_NAME_2 = "DataClass1Neo";
    private static final String DATA_CLASS_1_DESCRIPTION_2 = "DataClass1_new_description";
    private static final String DATA_CLASS_1_ALIAS_2 = "estAliasForDataClassSearchIndexing2";
    private static final String DATA_CLASS_1_COMMENT_2 = "A_different_comment_regarding_this_data_class";
    private static final String DATA_CLASS_2_NAME = "DataClass2";
    private static final String DATA_CLASS_3_NAME = "DataClass3";
    private static final String DATA_CLASS_ICE_CREAM = "cookies and cream";
    private static String dataClassDomainId;
    private static String dataClassDomainUri;
    private static long[] dataClassRowIds = new long[3];
    private static Connection connection;

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
    protected String getProjectName()
    {
        return "SearchTest" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES + " Project";
    }

    @Before
    public void preTest()
    {
        _containerHelper.deleteProject(getProjectName(), false);
        _searchHelper.initialize();
        openProjectMenu();
        int response = -1;
        try{
            response = WebTestHelper.getHttpGetResponse(getBaseURL() + "/" + WebTestHelper.stripContextPath(getAttribute(Locator.linkWithText(getProjectName()), "href")));
        }
        catch (NoSuchElementException | IOException ignore){/*No link or bad response*/}

        if (HttpStatus.SC_OK != response)
        {
            _containerHelper.createProject(getProjectName(), null);
        }
    }

    @Test
    public void testSearch()
    {
        SearchAdminAPIHelper.setDirectoryType(directoryType(), getDriver());
        connection = createDefaultConnection(true);
        // NOTE: the following test methods all depend on state from each other
        addSearchableDataClasses();
        modifySearchableDataClasses();
        deleteSearchableDataClasses();
        // TODO: add moving to another folder and verifying that data classes are still indexed (in their new location)
    }

    public void runApiTests() throws Exception
    {
        /* No API tests */
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
        if (afterTest)
        {
            _searchHelper.enqueueSearchItem(DATA_CLASS_2_NAME);
            _searchHelper.verifyNoSearchResults();
            _searchHelper.clearSearchQueue();
        }
    }

    private void addSearchableDataClasses()
    {
        log("Testing adding searchable data classes.");

        // create first domain
        PostCommand createDomainCommand = new PostCommand("property", "createDomain");
        JSONObject domainJson = new JSONObject();
        domainJson.put("kind", "DataClass");
        JSONArray domainFieldsJson = new JSONArray();
        JSONObject firstDomainFieldJson = new JSONObject();
        firstDomainFieldJson.put("name", "iceCreamFlavor");
        firstDomainFieldJson.put("rangeURI", "string");
        domainFieldsJson.add(firstDomainFieldJson);
        JSONObject secondDomainFieldJson = new JSONObject();
        secondDomainFieldJson.put("name", "foodColor");
        secondDomainFieldJson.put("rangeURI", "string");
        domainFieldsJson.add(secondDomainFieldJson);
        JSONObject thirdDomainFieldJson = new JSONObject();
        thirdDomainFieldJson.put("name", "sequence");
        thirdDomainFieldJson.put("rangeURI", "multiLine");
        domainFieldsJson.add(thirdDomainFieldJson);
        JSONObject domainDesignJson = new JSONObject();
        domainDesignJson.put("name", DATA_CLASS_DOMAIN_1);
        domainDesignJson.put("fields", domainFieldsJson);
        domainJson.put("domainDesign", domainDesignJson);
        JSONObject domainOptions = new JSONObject();
        domainOptions.put("description", DATA_CLASS_DOMAIN_DESCRIPTION);  // not tested currently TODO: make this work, not currently working
        domainOptions.put("nameExpression", DATA_CLASS_DOMAIN_EXPRESSION);  // not tested currently
        domainJson.put("options", domainOptions);
        createDomainCommand.setJsonObject(domainJson);
        CommandResponse commandResponse;
        try
        {
            commandResponse = createDomainCommand.execute(connection, getCurrentContainerPath());
        }
        catch (CommandException | IOException fail)
        {
            throw new RuntimeException(fail);
        }
        dataClassDomainId = commandResponse.getProperty("domainId");
        dataClassDomainUri = commandResponse.getProperty("domainURI");

        // create second domain with same fields as first
        createDomainCommand = new PostCommand("property", "createDomain");
        domainJson = new JSONObject();
        domainJson.put("kind", "DataClass");
        domainFieldsJson = new JSONArray();
        firstDomainFieldJson = new JSONObject();
        firstDomainFieldJson.put("name", "iceCreamFlavor");
        firstDomainFieldJson.put("rangeURI", "string");
        domainFieldsJson.add(firstDomainFieldJson);
        secondDomainFieldJson = new JSONObject();
        secondDomainFieldJson.put("name", "foodColor");
        secondDomainFieldJson.put("rangeURI", "string");
        domainFieldsJson.add(secondDomainFieldJson);
        thirdDomainFieldJson = new JSONObject();
        thirdDomainFieldJson.put("name", "sequence");
        thirdDomainFieldJson.put("rangeURI", "multiLine");
        domainFieldsJson.add(thirdDomainFieldJson);
        domainDesignJson = new JSONObject();
        domainDesignJson.put("name", DATA_CLASS_DOMAIN_2);
        domainDesignJson.put("fields", domainFieldsJson);
        domainJson.put("domainDesign", domainDesignJson);
        domainOptions = new JSONObject();
        domainOptions.put("description", DATA_CLASS_DOMAIN_DESCRIPTION);  // not tested currently TODO: make this work, not currently working
        domainOptions.put("nameExpression", DATA_CLASS_DOMAIN_EXPRESSION);  // not tested currently
        domainJson.put("options", domainOptions);
        createDomainCommand.setJsonObject(domainJson);
        try
        {
            commandResponse = createDomainCommand.execute(connection, getCurrentContainerPath());
        }
        catch (CommandException | IOException fail)
        {
            throw new RuntimeException(fail);
        }

        // create data classes using first domain above
        PostCommand insertRowsCommand = new PostCommand("query", "insertRows");
        domainJson = new JSONObject();
        domainJson.put("schemaName", DATA_CLASS_SCHEMA);
        domainJson.put("queryName", DATA_CLASS_DOMAIN_1);
        JSONArray domainRowsJson = new JSONArray();
        JSONObject firstDomainRowJson = new JSONObject();
        firstDomainRowJson.put("name", DATA_CLASS_1_NAME_1);
        firstDomainRowJson.put("description", DATA_CLASS_1_DESCRIPTION_1);
        firstDomainRowJson.put("alias", DATA_CLASS_1_ALIAS_1);
        firstDomainRowJson.put("comment", DATA_CLASS_1_COMMENT_1);
        firstDomainRowJson.put("iceCreamFlavor", "strawberry");
        firstDomainRowJson.put("foodColor", "pink");
        firstDomainRowJson.put("sequence", "hachi\nroku");
        domainRowsJson.add(firstDomainRowJson);
        JSONObject secondDomainRowJson = new JSONObject();
        secondDomainRowJson.put("name", DATA_CLASS_2_NAME);
        secondDomainRowJson.put("iceCreamFlavor", "vanilla");
        secondDomainRowJson.put("foodColor", "white");
        secondDomainRowJson.put("sequence", "ichi\nni\nsan");
        domainRowsJson.add(secondDomainRowJson);
        domainJson.put("rows", domainRowsJson);
        insertRowsCommand.setJsonObject(domainJson);
        try
        {
            commandResponse = insertRowsCommand.execute(connection, getCurrentContainerPath());
        }
        catch (CommandException | IOException fail)
        {
            throw new RuntimeException(fail);
        }

        JSONArray rows = commandResponse.getProperty("rows");
        JSONObject firstRow = (JSONObject)rows.get(0);
        //JSONObject secondRow = (JSONObject)rows.get(1);
        dataClassRowIds[0] = (Long)firstRow.get("rowid");
        //dataClassRowIds[1] = (Long)secondRow.get("rowid");

        // verify searchable data classes
        _searchHelper.clearSearchQueue();  // get rid of other searches
        _searchHelper.enqueueSearchItem("strawberry", Locator.linkContainingText(DATA_CLASS_1_NAME_1));
        _searchHelper.enqueueSearchItem(DATA_CLASS_1_DESCRIPTION_1, Locator.linkContainingText(DATA_CLASS_1_NAME_1));
        //_searchHelper.enqueueSearchItem(DATA_CLASS_1_SINGLE_ALIAS_1, Locator.linkContainingText(DATA_CLASS_1_NAME_1));
        _searchHelper.enqueueSearchItem(DATA_CLASS_1_COMMENT_1, Locator.linkContainingText(DATA_CLASS_1_NAME_1));
        _searchHelper.enqueueSearchItem("ichi \nni \nsan", Locator.linkContainingText(DATA_CLASS_2_NAME));  // spaces needed because search strips newlines
        _searchHelper.verifySearchResults("/" + getProjectName(), false);
        _searchHelper.clearSearchQueue();  // so we don't use these queries again
    }

    private void modifySearchableDataClasses()
    {
        log("Testing modifying searchable data classes.");

        // update data classes
        PostCommand updateRowsCommand = new PostCommand("query", "updateRows");
        JSONObject domainJson = new JSONObject();
        domainJson.put("schemaName", DATA_CLASS_SCHEMA);
        domainJson.put("queryName", DATA_CLASS_DOMAIN_1);
        JSONArray domainRowsJson = new JSONArray();
        JSONObject firstDomainRowJson = new JSONObject();
        firstDomainRowJson.put("rowId", dataClassRowIds[0]);
        firstDomainRowJson.put("name", DATA_CLASS_1_NAME_2);
        firstDomainRowJson.put("description", DATA_CLASS_1_DESCRIPTION_2);
        firstDomainRowJson.put("alias", DATA_CLASS_1_ALIAS_2);
        firstDomainRowJson.put("comment", DATA_CLASS_1_COMMENT_2);
        firstDomainRowJson.put("iceCreamFlavor", DATA_CLASS_ICE_CREAM);
        firstDomainRowJson.put("foodColor", "speckled");
        firstDomainRowJson.put("sequence", "ek\ndo\nteen");
        domainRowsJson.add(firstDomainRowJson);
        domainJson.put("rows", domainRowsJson);
        updateRowsCommand.setJsonObject(domainJson);
        CommandResponse commandResponse;
        try
        {
            commandResponse = updateRowsCommand.execute(connection, getCurrentContainerPath());
        }
        catch (CommandException | IOException fail)
        {
            throw new RuntimeException(fail);
        }

        _searchHelper.enqueueSearchItem("strawberry");  // these six items should no longer be found, as data class was modified
        _searchHelper.enqueueSearchItem("hachi \nroku");
        _searchHelper.enqueueSearchItem(DATA_CLASS_1_NAME_1);
        _searchHelper.enqueueSearchItem(DATA_CLASS_1_DESCRIPTION_1);
        //_searchHelper.enqueueSearchItem(DATA_CLASS_1_ALIAS_1);  TODO: re-enable this and related lines when alias indexing is added
        _searchHelper.enqueueSearchItem(DATA_CLASS_1_COMMENT_1);
        _searchHelper.enqueueSearchItem(DATA_CLASS_1_DESCRIPTION_2, Locator.linkContainingText(DATA_CLASS_1_NAME_2));
        //_searchHelper.enqueueSearchItem(DATA_CLASS_1_ALIAS_2, Locator.linkContainingText(DATA_CLASS_1_NAME_2));
        _searchHelper.enqueueSearchItem(DATA_CLASS_1_COMMENT_2, Locator.linkContainingText(DATA_CLASS_1_NAME_2));
        _searchHelper.enqueueSearchItem("speckled", Locator.linkContainingText(DATA_CLASS_1_NAME_2));
        _searchHelper.enqueueSearchItem("ek \ndo \nteen", Locator.linkContainingText(DATA_CLASS_1_NAME_2));
        _searchHelper.verifySearchResults("/" + getProjectName(), false);
        _searchHelper.clearSearchQueue();

        // create data class using second domain above and test domain ID indexing
        PostCommand insertRowsCommand = new PostCommand("query", "insertRows");
        domainJson = new JSONObject();
        domainJson.put("schemaName", DATA_CLASS_SCHEMA);
        domainJson.put("queryName", DATA_CLASS_DOMAIN_2);
        domainRowsJson = new JSONArray();
        firstDomainRowJson = new JSONObject();
        firstDomainRowJson.put("name", DATA_CLASS_3_NAME);
        firstDomainRowJson.put("iceCreamFlavor", "raspberry");
        domainRowsJson.add(firstDomainRowJson);
        domainJson.put("rows", domainRowsJson);
        insertRowsCommand.setJsonObject(domainJson);
        try
        {
            commandResponse = insertRowsCommand.execute(connection, getCurrentContainerPath());
        }
        catch (CommandException | IOException fail)
        {
            throw new RuntimeException(fail);
        }
        JSONArray rows = commandResponse.getProperty("rows");
        JSONObject firstRow = (JSONObject)rows.get(0);
        dataClassRowIds[2] = (Long)firstRow.get("rowid");

        _searchHelper.enqueueSearchItem("dataclass:" + DATA_CLASS_DOMAIN_1 + " AND " + DATA_CLASS_ICE_CREAM,
                Locator.linkContainingText(DATA_CLASS_1_NAME_1));
        _searchHelper.enqueueSearchItem("dataclass:" + DATA_CLASS_DOMAIN_2 + " AND " + DATA_CLASS_ICE_CREAM);  // should not return result for this data class
        _searchHelper.verifySearchResults("/" + getProjectName(), false);
        _searchHelper.clearSearchQueue();

        // delete data class with second domain (to avoid messing up later tests)
        PostCommand deleteRowsCommand = new PostCommand("query", "deleteRows");
        domainJson = new JSONObject();
        domainJson.put("schemaName", DATA_CLASS_SCHEMA);
        domainJson.put("queryName", DATA_CLASS_DOMAIN_2);
        domainRowsJson = new JSONArray();
        firstDomainRowJson = new JSONObject();
        firstDomainRowJson.put("rowId", dataClassRowIds[2]);
        domainRowsJson.add(firstDomainRowJson);
        domainJson.put("rows", domainRowsJson);
        deleteRowsCommand.setJsonObject(domainJson);
        try
        {
            commandResponse = deleteRowsCommand.execute(connection, getCurrentContainerPath());
        }
        catch (CommandException | IOException fail)
        {
            throw new RuntimeException(fail);
        }

        // alter domain
        // errors probably caused by Issue 26116, disable for now TODO: re-enable when Issue 26116 is fixed
        /*
        PostCommand saveDomainCommand = new PostCommand("property", "saveDomain");
        domainJson = new JSONObject();
        domainJson.put("schemaName", DATA_CLASS_SCHEMA);
        domainJson.put("queryName", DATA_CLASS_DOMAIN);
        JSONArray domainFieldsJson = new JSONArray();
        JSONObject firstDomainFieldJson = new JSONObject();
        firstDomainFieldJson.put("name", "sodaFlavor");
        firstDomainFieldJson.put("rangeURI", "string");
        domainFieldsJson.add(firstDomainFieldJson);
        JSONObject secondDomainFieldJson = new JSONObject();
        secondDomainFieldJson.put("name", "foodColor");
        secondDomainFieldJson.put("rangeURI", "string");
        domainFieldsJson.add(secondDomainFieldJson);
        JSONObject thirdDomainFieldJson = new JSONObject();
        thirdDomainFieldJson.put("name", "sequence");
        thirdDomainFieldJson.put("rangeURI", "multiLine");
        domainFieldsJson.add(thirdDomainFieldJson);
        JSONObject domainDesignJson = new JSONObject();
        domainDesignJson.put("domainId", dataClassDomainId);
        domainDesignJson.put("domainURI", dataClassDomainUri);
        domainDesignJson.put("fields", domainFieldsJson);
        domainJson.put("domainDesign", domainDesignJson);
        JSONObject domainOptions = new JSONObject();
        domainJson.put("options", domainOptions);
        saveDomainCommand.setJsonObject(domainJson);
        try
        {
            commandResponse = saveDomainCommand.execute(connection, getCurrentContainerPath());
        }
        catch (CommandException | IOException fail)
        {
            throw new RuntimeException(fail);
        }

        // update classes with altered domain
        PostCommand saveRowsCommand = new PostCommand("query", "updateRows");
        domainJson = new JSONObject();
        domainJson.put("schemaName", DATA_CLASS_SCHEMA);
        domainJson.put("queryName", DATA_CLASS_DOMAIN);
        domainRowsJson = new JSONArray();
        JSONObject secondDomainRowJson = new JSONObject();
        secondDomainRowJson.put("rowId", dataClassRowIds[1]);
        secondDomainRowJson.put("sodaFlavor", "ginger ale");
        secondDomainRowJson.put("foodColor", "yellow");
        secondDomainRowJson.put("sequence", "ein \nzwei \ndrei");
        domainRowsJson.add(secondDomainRowJson);
        domainJson.put("rows", domainRowsJson);
        saveRowsCommand.setJsonObject(domainJson);
        try
        {
            commandResponse = saveRowsCommand.execute(connection, getCurrentContainerPath());
        }
        catch (CommandException | IOException fail)
        {
            throw new RuntimeException(fail);
        }
        _searchHelper.enqueueSearchItem("speckled");  // should no longer be found, as domain fields were modified
        _searchHelper.enqueueSearchItem("ek \ndo \nteen");  // should no longer be found, as domain fields were modified
        _searchHelper.enqueueSearchItem("yellow", Locator.linkContainingText(DATA_CLASS_2_NAME));
        _searchHelper.enqueueSearchItem("ein \nzwei \ndrei", Locator.linkContainingText(DATA_CLASS_2_NAME));
        _searchHelper.verifySearchResults("/" + getProjectName(), false);
        _searchHelper.clearSearchQueue();
        */
    }

    private void deleteSearchableDataClasses()
    {
        log("Testing deleting searchable data classes.");

        // delete data classes with first domain
        PostCommand deleteRowsCommand = new PostCommand("query", "deleteRows");
        JSONObject domainJson = new JSONObject();
        domainJson.put("schemaName", DATA_CLASS_SCHEMA);
        domainJson.put("queryName", DATA_CLASS_DOMAIN_1);
        JSONArray domainRowsJson = new JSONArray();
        JSONObject firstDomainRowJson = new JSONObject();
        firstDomainRowJson.put("rowId", dataClassRowIds[0]);
        domainRowsJson.add(firstDomainRowJson);
        domainJson.put("rows", domainRowsJson);
        deleteRowsCommand.setJsonObject(domainJson);
        CommandResponse commandResponse;
        try
        {
            commandResponse = deleteRowsCommand.execute(connection, getCurrentContainerPath());
        }
        catch (CommandException | IOException fail)
        {
            throw new RuntimeException(fail);
        }
        _searchHelper.enqueueSearchItem(DATA_CLASS_1_NAME_2);  // this and remaining search items should no longer be found, as data classes were removed
        //_searchHelper.enqueueSearchItem("yellow");  // TODO: re-enable this and following line when Issue 26116 is fixed
        //_searchHelper.enqueueSearchItem("ein \nzwei \ndrei");
        _searchHelper.verifySearchResults("/" + getProjectName(), false);
        _searchHelper.clearSearchQueue();
    }

    @Override @Test @Ignore
    public void testSteps() throws Exception
    {
        // Mask parent test
    }
}
