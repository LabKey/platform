/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyC;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.LogMethod;

import java.io.File;

@Category({DailyC.class})
public class StudyDatasetIndexTest extends StudyBaseTest
{
    private static final File STUDY_WITH_DATASET_INDEX = TestFileUtils.getSampleData("studies/StudyWithDatasetIndex.folder.zip");
    private static final File STUDY_WITH_DATASET_SHARED_INDEX = TestFileUtils.getSampleData("studies/StudyWithDatasetSharedIndex.folder.zip");
    private static final String METADATA = "Table Meta Data";

    protected String getProjectName()
    {
        return "StudyDatasetSharedColumnAndIndexProject";
    }

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
    protected void doVerifySteps()
    {
        beginAt("/query/" + getProjectName() + "/" + getFolderName()  + "/schema.view?schemaName=study");
        selectQuery("study", "DEM-1");
        waitForText(10000, "view raw table metadata");
        clickAndWait(Locator.linkWithText("view raw table metadata"));
        assertTextPresentCaseInsensitive("dem_minus_1_indexedColumn");

        reloadStudyFromZip(STUDY_WITH_DATASET_SHARED_INDEX);

        beginAt("/query/" + getProjectName() + "/" + getFolderName()  + "/schema.view?schemaName=study");
        selectQuery("study", "DEM-1");
        waitForText(10000, "view raw table metadata");
        clickAndWait(Locator.linkWithText("view raw table metadata"));
        assertTextPresentCaseInsensitive("dem_minus_1_indexedColumn");
        assertTextPresentCaseInsensitive("dem_minus_1_sharedColumn");

        int colNameIndex = 0;
        int colSizeIndex = 3;
        if (WebTestHelper.getDatabaseType() == WebTestHelper.DatabaseType.PostgreSQL)
        {
            colNameIndex = 3;
            colSizeIndex = 6;
        }

        // Verify size column specified in datasets_metadata
        assertTableRowInNonDataRegionTable(METADATA, "sharedcolumn", 33, colNameIndex);
        assertTableRowInNonDataRegionTable(METADATA, "20", 33, colSizeIndex);

        // Verify default sizes
        assertTableRowInNonDataRegionTable(METADATA, "multilinecolumn", 31, colNameIndex);
        assertTableRowInNonDataRegionTable(METADATA, "4000", 31, colSizeIndex);

        assertTableRowInNonDataRegionTable(METADATA, "flagcolumn", 32, colNameIndex);
        assertTableRowInNonDataRegionTable(METADATA, "4000", 32, colSizeIndex);

        beginAt("/query/" + getProjectName() + "/" + getFolderName()  + "/schema.view?schemaName=study");
        selectQuery("study", "DEM-2");
        waitForText(10000, "view raw table metadata");
        clickAndWait(Locator.linkWithText("view raw table metadata"));
        assertTextNotPresent("indexedColumn");
        assertTextPresentCaseInsensitive("dem_minus_2_sharedColumn");
    }
}
