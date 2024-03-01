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
package org.labkey.test.tests.study;

import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.TruncateTableCommand;
import org.labkey.remoteapi.query.TruncateTableResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.pipeline.PipelineStatusDetailsPage;
import org.labkey.test.pages.study.DatasetDesignerPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.LogMethod;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 12)
public class StudyDatasetIndexTest extends StudyBaseTest
{
    private static final File STUDY_WITH_DATASET_INDEX = TestFileUtils.getSampleData("studies/StudyWithDatasetIndex.folder.zip");
    private static final File STUDY_WITH_DATASET_SHARED_INDEX = TestFileUtils.getSampleData("studies/StudyWithDatasetSharedIndex.folder.zip");
    private static final String METADATA = "Table Meta Data";

    @Override
    protected String getProjectName()
    {
        return "StudyDatasetSharedColumnAndIndexProject";
    }

    @Override
    protected String getFolderName()
    {
        return "Study Dataset Shared Column and Index";
    }

    @Override
    @LogMethod
    protected void doCreateSteps()
    {
        initializeFolder();
        initializePipeline(null);
        clickFolder(getFolderName());

        log("Import study with index on dataset");
        importFolderFromZip(STUDY_WITH_DATASET_INDEX);
    }

    @Override
    @LogMethod
    protected void doVerifySteps() throws Exception
    {
        viewRawTableMetadata("DEM-1");
        verifyTableIndices("dem_minus_1_", Collections.emptyList());

        // verify column specific index
        assertTextPresentCaseInsensitive("dem_minus_1_indexedColumn");

        // Issue: 44363 - we are expecting the import to fail because we are trying to change dataset keys on a
        // non-empty dataset.
        assertEquals("Server errors detected", 0, getServerErrorCount());
        reloadStudyFromZip(STUDY_WITH_DATASET_SHARED_INDEX, true, 2, true);
        clickAndWait(Locator.linkWithText("ERROR"));
        new PipelineStatusDetailsPage(getDriver()).assertLogTextContains("ERROR: Unable to change the keys on dataset (DEM-1), because there is still data present. The dataset should be truncated before the import.");
        resetErrors();

        // truncate the table
        log("Truncating the table");
        final Connection cn = WebTestHelper.getRemoteApiConnection(true);
        TruncateTableCommand cmd = new TruncateTableCommand("study", "DEM-1");
        TruncateTableResponse resp = cmd.execute(cn, getProjectName() + "/" + getFolderName());
        assertEquals("Truncation of DEM-1 table failed", 24, (int) resp.getDeletedRowCount());

        // reload should now work
        deleteAllPipelineJobs();
        reloadStudyFromZip(STUDY_WITH_DATASET_SHARED_INDEX, true, 1, false);

        viewRawTableMetadata("DEM-1");
        verifyTableIndices("dem_minus_1_", List.of("indexedcolumn", "sharedcolumn"));

        int colNameIndex = 3;
        int colSizeIndex = 6;
//        if (WebTestHelper.getDatabaseType() == WebTestHelper.DatabaseType.PostgreSQL)
//        {
//            colNameIndex = 3;
//            colSizeIndex = 6;
//        }

        // related BUG https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=42229
        // Verify size column specified in datasets_metadata
        assertTableRowInNonDataRegionTable(METADATA, "sharedcolumn", 34, colNameIndex);
        assertTableRowInNonDataRegionTable(METADATA, "20", 34, colSizeIndex);

        // Verify default sizes
        assertTableRowInNonDataRegionTable(METADATA, "multilinecolumn", 32, colNameIndex);
        assertTableRowInNonDataRegionTable(METADATA, "4000", 32, colSizeIndex);

        assertTableRowInNonDataRegionTable(METADATA, "flagcolumn", 33, colNameIndex);
        assertTableRowInNonDataRegionTable(METADATA, "4000", 33, colSizeIndex);

        viewRawTableMetadata("DEM-2");
        assertTextNotPresent("indexedColumn");
        verifyTableIndices("dem_minus_2_", List.of("sharedcolumn"));

        // create a new dataset with 2 fields that initially have a unique constraint
        DatasetDesignerPage datasetDesignerPage = goToManageStudy().manageDatasets().clickCreateNewDataset();
        datasetDesignerPage.setName("DEM-3");
        String fieldName1 = "field Name1";
        String fieldName2 = "fieldName_2";
        String fieldName3 = "FieldName@3";
        datasetDesignerPage.getFieldsPanel()
                .addField(fieldName1)
                .setType(FieldDefinition.ColumnType.Integer)
                .expand()
                .clickAdvancedSettings()
                .setUniqueConstraint(true)
                .apply();
        datasetDesignerPage.getFieldsPanel()
                .addField(fieldName2)
                .setType(FieldDefinition.ColumnType.DateAndTime)
                .expand()
                .clickAdvancedSettings()
                .setUniqueConstraint(true)
                .apply();
        datasetDesignerPage.getFieldsPanel()
                .addField(fieldName3)
                .setType(FieldDefinition.ColumnType.Boolean);
        datasetDesignerPage.clickSave();

        viewRawTableMetadata("DEM-3");
        verifyTableIndices("dem_minus_3_", List.of("field_name1", "fieldname_2"));
        assertTextNotPresent("dem_minus_3_fieldname_3");

        // remove a field unique constraint and add a new one
        goBack();
        datasetDesignerPage = goToEditDatasetDefinition("DEM-3");
        datasetDesignerPage.getFieldsPanel()
                .getField(fieldName2).expand().clickAdvancedSettings().setUniqueConstraint(false)
                .apply();
        datasetDesignerPage.getFieldsPanel()
                .getField(fieldName3).expand().clickAdvancedSettings().setUniqueConstraint(true)
                .apply();
        datasetDesignerPage.clickSave();

        viewRawTableMetadata("DEM-3");
        verifyTableIndices("dem_minus_3_", List.of("field_name1", "fieldname_3"));
        assertTextNotPresent("dem_minus_3_fieldname_2");
    }

    private DatasetDesignerPage goToEditDatasetDefinition(String datasetName)
    {
        return goToManageStudy()
                .manageDatasets()
                .selectDatasetByName(datasetName)
                .clickEditDefinition();
    }

    private void viewRawTableMetadata(String datasetName)
    {
        beginAt("/query/" + getProjectName() + "/" + getFolderName()  + "/schema.view?schemaName=study");
        selectQuery("study", datasetName);
        waitForText(10000, "view raw table metadata");
        clickAndWait(Locator.linkWithText("view raw table metadata"));
    }

    private void verifyTableIndices(String prefix, List<String> indexSuffixes)
    {
        List<String> suffixes  = new ArrayList<>();
        suffixes.add("lsid");
        suffixes.add("participantid_sequencenum__key");
        suffixes.add("pk");
        suffixes.add("participantid_date");
        suffixes.add("date");
        suffixes.add("participantsequencenum");
        suffixes.add("qcstate");
        suffixes.addAll(indexSuffixes);

        for (String suffix : suffixes)
            assertTextPresentCaseInsensitive(prefix + suffix);
    }
}
