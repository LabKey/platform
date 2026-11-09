/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.test.tests.query;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.query.ExecuteQueryPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.params.FieldInfo;
import org.labkey.test.params.experiment.DataClassDefinition;
import org.labkey.test.util.DomainUtils;
import org.labkey.test.util.exp.DataClassAPIHelper;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 * use this test as a place to put cross-folder dataclass testing
 */
@Category({Daily.class})
public class CrossFolderDataClassTest extends BaseWebDriverTest
{
    private static final String SUBFOLDER_A = "subA";
    private static String SUBFOLDER_A_PATH;

    @Override
    protected void doCleanup(boolean afterTest)
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    @BeforeClass
    public static void setupProject()
    {
        CrossFolderDataClassTest init = getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), null);
        _containerHelper.createSubfolder(getProjectName(), SUBFOLDER_A);
        SUBFOLDER_A_PATH = getProjectName() + "/" + SUBFOLDER_A;
    }

    /**
     * Issue 45664: addresses the problem where DataClass metadata wasn't available in query when querying cross-folder
     */
    @Test
    public void testIssue45664() throws Exception
    {
        String dataClass = "TopFolderDataClass";
        var fields = Arrays.asList(
                FieldInfo.random("intColumn", FieldDefinition.ColumnType.Integer, DomainUtils.DomainKind.DataClass).getFieldDefinition(),
                FieldInfo.random("decimalColumn", FieldDefinition.ColumnType.Decimal, DomainUtils.DomainKind.DataClass).getFieldDefinition(),
                FieldInfo.random("stringColumn", FieldDefinition.ColumnType.String, DomainUtils.DomainKind.DataClass).getFieldDefinition(),
                FieldInfo.random("sampleDate", FieldDefinition.ColumnType.DateAndTime, DomainUtils.DomainKind.DataClass).getFieldDefinition(),
                FieldInfo.random("boolColumn", FieldDefinition.ColumnType.Boolean, DomainUtils.DomainKind.DataClass).getFieldDefinition()
        );
        // make a dataclass in the top folder, give it some data
        DataClassDefinition testType = new DataClassDefinition(dataClass).setFields(fields);
        var dGen = DataClassAPIHelper.createEmptyDataClass(getProjectName(), testType);
        dGen.generateRows(3);
        dGen.insertRows();

        // now view the dataclass from a subfolder, expanding its view to include all folders and to show rowId and DataClass/Name
        var subfolderQueryPage = ExecuteQueryPage.beginAt(this, SUBFOLDER_A_PATH, "exp.data", dataClass);

        var customizeView = subfolderQueryPage.getDataRegion().openCustomizeGrid();
        customizeView.showHiddenItems();
        customizeView.addColumn("RowId");
        customizeView.addColumn("DataClass/Name");
        customizeView.saveDefaultView();

        // now insert a record into the dataclass, in the subfolder
        subfolderQueryPage.getDataRegion().clickInsertNewRow()
                .setField("Name", "Jeff")
                .setField(testType.getFieldByNamePart("intColumn").getName(), "5")
                .setField(testType.getFieldByNamePart("decimalColumn").getName(), "6.7")
                .setField(testType.getFieldByNamePart("stringColumn").getName(), "hey")
                .submit();

        // gather the data from the view; should only see Jeff
        var shownData = subfolderQueryPage.getDataRegion().getTableData();

        // verify expected container filtering; just the 1 record in the current container should be visible
        assertEquals("Expect grid to only show records in the current container",
                1, shownData.size());

        // ensure the record shows the expected rowId and DataClass/Name values
        var newRecord = shownData.getFirst();
        assertEquals("Expect Jeff to be the name value",
                "Jeff", newRecord.get("Name"));
        assertNotNull("Expect metadata rowId to be shown in subfolder view",
                newRecord.get("rowid"));
        assertEquals("Expect metadata DataClass/Name to be shown in modified subfolder view",
                dataClass, newRecord.get("DataClass/Name"));

        // ensure the 'name' is linked up and navigates to experiment-showData.view
        clickAndWait(Locator.linkWithText("Jeff"));
        assertThat("name link should click through to showdata page",
                getURL().toString().toLowerCase(), containsString("experiment-showdata.view"));
    }

    @Override
    protected String getProjectName()
    {
        return "CrossFolderDataClassTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList();
    }
}
