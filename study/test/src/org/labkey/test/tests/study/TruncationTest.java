/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyA;
import org.labkey.test.categories.Specimen;
import org.labkey.test.pages.DatasetPropertiesPage;
import org.labkey.test.pages.ManageDatasetsPage;
import org.labkey.test.util.Ext4Helper;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({DailyA.class, Specimen.class})
public class TruncationTest extends BaseWebDriverTest
{
    private final File LIST_ARCHIVE = TestFileUtils.getSampleData("lists/searchTest.lists.zip");
    private final String LIST_NAME = "List1";

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Nullable
    @Override
    protected String getProjectName()
    {
        return "TruncationTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    @BeforeClass
    public static void doSetup()
    {
        TruncationTest init = (TruncationTest)getCurrentTest();
        init.initTest();
    }

    private void initTest()
    {
        _containerHelper.createProject(getProjectName(), "Study");
        importFolderFromZip(TestFileUtils.getSampleData("studies/AltIdStudy.folder.zip"));
        _listHelper.importListArchive(getProjectName(), LIST_ARCHIVE);
    }

    @Test
    public void testTruncateList()
    {
        goToProjectHome();
        clickAndWait(Locator.linkWithText(LIST_NAME));
        click(Locator.linkContainingText("Delete All Rows"));
        waitAndClick(Ext4Helper.Locators.ext4Button("Yes"));
        waitForText("2 rows deleted");
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("OK"));
        waitForText("No data to show.");
    }

    @Test
    public void testTruncateDataset()
    {
        goToProjectHome();
        waitAndClickAndWait(Locator.linkContainingText("Manage Datasets"));
        DatasetPropertiesPage.ResultWindow resultWindow = new ManageDatasetsPage(getDriver())
                .selectDatasetByName("DEM-1")
                .deleteAllRows();

        assertEquals("Truncation result", "24 rows deleted", resultWindow.getMessage());
        resultWindow
                .accept()
                .clickViewData();

        waitForText("No data to show.");
    }

    @Test
    public void testTruncateVisibility()
    {
        goToProjectHome();
        impersonateRole("Editor");
        clickAndWait(Locator.linkWithText(LIST_NAME));
        assertTextNotPresent("Delete All Rows");
        stopImpersonating();
    }
}