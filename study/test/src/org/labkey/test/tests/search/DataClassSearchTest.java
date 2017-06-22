/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.PostCommand;
import org.labkey.remoteapi.query.DeleteRowsCommand;
import org.labkey.remoteapi.query.InsertRowsCommand;
import org.labkey.remoteapi.query.SaveRowsResponse;
import org.labkey.remoteapi.query.UpdateRowsCommand;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyA;
import org.labkey.test.categories.Search;
import org.labkey.test.util.SearchHelper;
import org.labkey.test.util.search.SearchAdminAPIHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Category({DailyA.class, Search.class})
public class DataClassSearchTest extends BaseWebDriverTest
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
        return "DataClass SearchTest" + " Project";
    }

    @BeforeClass
    public static void initProject()
    {
        DataClassSearchTest init = (DataClassSearchTest)getCurrentTest();

        init.doInit();
    }

    private void doInit()
    {
        _containerHelper.createProject(getProjectName(), null);
        _searchHelper.initialize();
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void testSearch() throws Exception
    {
        SearchAdminAPIHelper.setDirectoryType(directoryType(), getDriver());
        connection = createDefaultConnection(true);
        // NOTE: the following test methods all depend on state from each other
        addSearchableDataClasses();
        modifySearchableDataClasses();
        deleteSearchableDataClasses();
        // TODO: add moving to another folder and verifying that data classes are still indexed (in their new location)
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

    private void addSearchableDataClasses() throws Exception
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
        commandResponse = createDomainCommand.execute(connection, getCurrentContainerPath());
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
        createDomainCommand.execute(connection, getCurrentContainerPath());

        // create data classes using first domain above
        InsertRowsCommand insertRowsCommand = new InsertRowsCommand(DATA_CLASS_SCHEMA, DATA_CLASS_DOMAIN_1);
        insertRowsCommand.setRequiredVersion(9.1);
        Map<String, Object> row1 = new HashMap<>();
        row1.put("name", DATA_CLASS_1_NAME_1);
        row1.put("description", DATA_CLASS_1_DESCRIPTION_1);
        row1.put("alias", DATA_CLASS_1_ALIAS_1);
        row1.put("comment", DATA_CLASS_1_COMMENT_1);
        row1.put("iceCreamFlavor", "strawberry");
        row1.put("foodColor", "pink");
        row1.put("sequence", "hachi\nroku");
        Map<String, Object> row2 = new HashMap<>();
        row2.put("name", DATA_CLASS_2_NAME);
        row2.put("iceCreamFlavor", "vanilla");
        row2.put("foodColor", "white");
        row2.put("sequence", "ichi\nni\nsan");
        insertRowsCommand.setRows(new ArrayList<>(Arrays.asList(row1, row2)));
        SaveRowsResponse insertResponse = insertRowsCommand.execute(connection, getCurrentContainerPath());

        List<Map<String, Object>> responseRows = insertResponse.getRows();
        dataClassRowIds[0] = (Long)responseRows.get(0).get("rowid");
        dataClassRowIds[1] = (Long)responseRows.get(1).get("rowid");

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

    private void modifySearchableDataClasses() throws Exception
    {
        log("Testing modifying searchable data classes.");

        // update data classes
        UpdateRowsCommand updateRowsCommand = new UpdateRowsCommand(DATA_CLASS_SCHEMA, DATA_CLASS_DOMAIN_1);
        Map<String, Object> updatedRow = new HashMap<>();
        updatedRow.put("rowId", dataClassRowIds[0]);
        updatedRow.put("name", DATA_CLASS_1_NAME_2);
        updatedRow.put("description", DATA_CLASS_1_DESCRIPTION_2);
        updatedRow.put("alias", DATA_CLASS_1_ALIAS_2);
        updatedRow.put("comment", DATA_CLASS_1_COMMENT_2);
        updatedRow.put("iceCreamFlavor", DATA_CLASS_ICE_CREAM);
        updatedRow.put("foodColor", "speckled");
        updatedRow.put("sequence", "ek\ndo\nteen");
        updateRowsCommand.addRow(updatedRow);
        updateRowsCommand.execute(connection, getCurrentContainerPath());

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
        InsertRowsCommand insertRowsCommand = new InsertRowsCommand(DATA_CLASS_SCHEMA, DATA_CLASS_DOMAIN_2);
        Map<String, Object> row1 = new HashMap<>();
        row1.put("name", DATA_CLASS_3_NAME);
        row1.put("iceCreamFlavor", "raspberry");
        insertRowsCommand.addRow(row1);
        SaveRowsResponse insertResponse = insertRowsCommand.execute(connection, getCurrentContainerPath());
        List<Map<String, Object>> responseRows = insertResponse.getRows();
        dataClassRowIds[2] = (Long)responseRows.get(0).get("rowid");

        _searchHelper.enqueueSearchItem("dataclass:" + DATA_CLASS_DOMAIN_1 + " AND " + DATA_CLASS_ICE_CREAM,
                Locator.linkContainingText(DATA_CLASS_1_NAME_1));
        _searchHelper.enqueueSearchItem("dataclass:" + DATA_CLASS_DOMAIN_2 + " AND " + DATA_CLASS_ICE_CREAM);  // should not return result for this data class
        _searchHelper.verifySearchResults("/" + getProjectName(), false);
        _searchHelper.clearSearchQueue();

        // delete data class with second domain (to avoid messing up later tests)
        DeleteRowsCommand deleteRowsCommand = new DeleteRowsCommand(DATA_CLASS_SCHEMA, DATA_CLASS_DOMAIN_2);
        Map<String, Object> deletedRow = new HashMap<>();
        deletedRow.put("rowId", dataClassRowIds[2]);
        deleteRowsCommand.addRow(deletedRow);
        deleteRowsCommand.execute(connection, getCurrentContainerPath());

        // commented out due to intermittently failing on TeamCity
        // TODO: if issue #26116 is ever resolved, re-enable this part of the test
        //testSearchAfterModifyingDomain();
    }

    private void testSearchAfterModifyingDomain() throws Exception
    {
        // alter domain

        PostCommand saveDomainCommand = new PostCommand("property", "saveDomain");
        JSONObject domainJson = new JSONObject();
        domainJson.put("schemaName", DATA_CLASS_SCHEMA);
        domainJson.put("queryName", DATA_CLASS_DOMAIN_1);
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
        saveDomainCommand.execute(connection, getCurrentContainerPath());

        // update classes with altered domain
        PostCommand saveRowsCommand = new PostCommand("query", "updateRows");
        domainJson = new JSONObject();
        domainJson.put("schemaName", DATA_CLASS_SCHEMA);
        domainJson.put("queryName", DATA_CLASS_DOMAIN_1);
        JSONArray domainRowsJson = new JSONArray();
        JSONObject secondDomainRowJson = new JSONObject();
        secondDomainRowJson.put("rowId", dataClassRowIds[1]);
        secondDomainRowJson.put("sodaFlavor", "ginger ale");
        secondDomainRowJson.put("foodColor", "yellow");
        secondDomainRowJson.put("sequence", "ein \nzwei \ndrei");
        domainRowsJson.add(secondDomainRowJson);
        domainJson.put("rows", domainRowsJson);
        saveRowsCommand.setJsonObject(domainJson);
        saveRowsCommand.execute(connection, getCurrentContainerPath());

        _searchHelper.enqueueSearchItem("speckled");  // should no longer be found, as domain fields were modified
        _searchHelper.enqueueSearchItem("ek \ndo \nteen");  // should no longer be found, as domain fields were modified
        _searchHelper.enqueueSearchItem("yellow", Locator.linkContainingText(DATA_CLASS_2_NAME));
        _searchHelper.enqueueSearchItem("ein \nzwei \ndrei", Locator.linkContainingText(DATA_CLASS_2_NAME));
        _searchHelper.verifySearchResults("/" + getProjectName(), false);
        _searchHelper.clearSearchQueue();
    }

    private void deleteSearchableDataClasses() throws Exception
    {
        log("Testing deleting searchable data classes.");

        // delete data classes with first domain
        DeleteRowsCommand deleteRowsCommand = new DeleteRowsCommand(DATA_CLASS_SCHEMA, DATA_CLASS_DOMAIN_1);
        Map<String, Object> deletedRow = new HashMap<>();
        deletedRow.put("rowId", dataClassRowIds[1]);
        deleteRowsCommand.addRow(deletedRow);
        deleteRowsCommand.execute(connection, getCurrentContainerPath());

        _searchHelper.enqueueSearchItem(DATA_CLASS_2_NAME);  // this and remaining search items should no longer be found, as data classes were removed
        _searchHelper.enqueueSearchItem("yellow");  // keep an eye on these and surrounding tests, see issue #26116
        _searchHelper.enqueueSearchItem("ein \nzwei \ndrei");
        _searchHelper.verifySearchResults("/" + getProjectName(), false);
        _searchHelper.clearSearchQueue();
    }
}
