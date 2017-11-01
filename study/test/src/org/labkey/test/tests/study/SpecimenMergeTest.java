/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Specimen;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * CreateVialsTest also uses the specimen merge feature.
 */
@Category({DailyB.class, Specimen.class})
public class SpecimenMergeTest extends BaseWebDriverTest
{
    protected static final String PROJECT_NAME = "SpecimenMergeTest";
    protected static final String FOLDER_NAME = "My Study";

    protected static final String LAB19_SPECIMENS = "/sampledata/study/specimens/lab19.specimens";
    protected static final String LAB20_SPECIMENS = "/sampledata/study/specimens/lab20.specimens";
    protected static final String LAB21_SPECIMENS = "/sampledata/study/specimens/lab21.specimens";

    protected static final String SPECIMEN_TEMP_DIR = "/sampledata/study/drt_temp";
    protected int pipelineJobCount = 3;

    protected String _studyDataRoot = null;

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _studyDataRoot = TestFileUtils.getLabKeyRoot() + "/sampledata/study";
        File tempDir = new File(TestFileUtils.getLabKeyRoot() + SPECIMEN_TEMP_DIR);
        if (tempDir.exists())
        {
            for (File file : tempDir.listFiles())
                file.delete();
            tempDir.delete();
        }
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    @Test
    public void testSteps()
    {
        setUpSteps();

        importFirstFileSet();
    }

    protected void importFirstFileSet()
    {
        File[] archives = new File[]{
                new File(TestFileUtils.getLabKeyRoot(), LAB19_SPECIMENS),
                new File(TestFileUtils.getLabKeyRoot(), LAB20_SPECIMENS),
                new File(TestFileUtils.getLabKeyRoot(), LAB21_SPECIMENS)
        };
        SpecimenImporter importer = new SpecimenImporter(new File(_studyDataRoot), archives, new File(TestFileUtils.getLabKeyRoot(), SPECIMEN_TEMP_DIR), FOLDER_NAME, pipelineJobCount);
        importer.setExpectError(true);
        importer.importAndWaitForComplete();

        // Check there was an error in the specimen merge.
        clickAndWait(Locator.linkWithText("ERROR"));
        assertTextPresent("lab20", "Conflicting specimens found for GlobalUniqueId 'AAA07XK5-02'");
        checkExpectedErrors(2);
    }

    protected void setUpSteps()
    {
        _studyDataRoot = TestFileUtils.getLabKeyRoot() + "/sampledata/study";

        _containerHelper.createProject(PROJECT_NAME, null);

        _containerHelper.createSubfolder(PROJECT_NAME, PROJECT_NAME, FOLDER_NAME, "Study", null);
        clickButton("Create Study");
        click(Locator.radioButtonByNameAndValue("simpleRepository", "true"));
        clickButton("Create Study");

        setPipelineRoot(_studyDataRoot);
        clickFolder("My Study");
        clickAndWait(Locator.linkWithText("Manage Files"));
    }

}
