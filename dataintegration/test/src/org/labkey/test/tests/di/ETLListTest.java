/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.test.tests.di;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.ETL;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Maps;
import org.labkey.test.util.di.DataIntegrationHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({DailyB.class, ETL.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class ETLListTest extends BaseWebDriverTest
{
    DataIntegrationHelper _etlHelper = new DataIntegrationHelper(getProjectName());
    private static final String ETL_LIST_MERGE = "{ETLtest}/ListAToListB";
    private static final String ETL_AUTO_INCR_LIST_TRUNCATE = "{ETLtest}/AutoIncrementListAToListB_truncate";
    // TODO: revert to ETL_ListAListB.lists.zip when 24725 is resolved. Delete xETL_ListAListB.lists.zip
    private static final File ETL_LIST_ARCHIVE = TestFileUtils.getSampleData("lists/xETL_ListAListB.lists.zip");
    private static final File ETL_AUTO_INCR_LIST_ARCHIVE = TestFileUtils.getSampleData("lists/ETL_AutoIncrListA_AutoIncrListB.lists.zip");
    private static final String SRC_LIST = "ListA";
    private static final String DEST_LIST = "ListB";
    private static final String AUTO_INCR_SRC_LIST = "AutoIncrementListA";
    private static final String AUTO_INCR_DEST_LIST = "AutoIncrementListB";


    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @BeforeClass
    public static void setupProject()
    {
        ETLListTest init = (ETLListTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), null);
        _containerHelper.enableModules(Arrays.asList("DataIntegration", "ETLtest"));
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
        _listHelper.importListArchive(ETL_LIST_ARCHIVE);
        goToProjectHome();
        _listHelper.importListArchive(ETL_AUTO_INCR_LIST_ARCHIVE);
    }

    @Test
    public void testMergeEtl() throws Exception
    {
        List<String> expectedKeys = new ArrayList<>(Arrays.asList("K1", "K3"));
        _etlHelper.runTransform(ETL_LIST_MERGE);

        clickAndWait(Locator.linkWithText(DEST_LIST));
        DataRegionTable dest = new DataRegionTable("query", this);
        List<String> actualKeys = dest.getColumnDataAsText("Key");
        assertEquals("Initial list copy failed", expectedKeys, actualKeys);

        goBack();
        clickAndWait(Locator.linkWithText(SRC_LIST));
        // TODO: switch back to "Key" when 24725 is resolved
        _listHelper.insertNewRow(Maps.of("xKey", "K4", "Field1", "new"));
        _etlHelper.runTransform(ETL_LIST_MERGE);
        expectedKeys.add("K4");

        clickAndWait(Locator.linkWithText(DEST_LIST));
        dest = new DataRegionTable("query", this);
        actualKeys = dest.getColumnDataAsText("Key");
        assertEquals("List merge failed", expectedKeys, actualKeys);
    }

    @Test
    public void testAutoIncrTruncateEtl() throws Exception
    {
        List<String> expectedRows = new ArrayList<>(Arrays.asList("Row1", "Row2"));
        _etlHelper.runTransform(ETL_AUTO_INCR_LIST_TRUNCATE);

        clickAndWait(Locator.linkWithText(AUTO_INCR_DEST_LIST));
        DataRegionTable dest = new DataRegionTable("query", this);
        List<String> actualRows = dest.getColumnDataAsText("Field1");
        assertEquals("Initial list copy failed", expectedRows, actualRows);

        goBack();
        clickAndWait(Locator.linkWithText(AUTO_INCR_SRC_LIST));
        _listHelper.insertNewRow(Maps.of("Field1", "new"));
        _etlHelper.runTransform(ETL_AUTO_INCR_LIST_TRUNCATE);
        expectedRows.add("new");

        clickAndWait(Locator.linkWithText(AUTO_INCR_DEST_LIST));
        dest = new DataRegionTable("query", this);
        actualRows = dest.getColumnDataAsText("Field1");
        assertEquals("Second list copy failed", expectedRows, actualRows);
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "ETLListTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("DataIntegration");
    }
}