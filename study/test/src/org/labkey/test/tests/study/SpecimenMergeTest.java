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

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Daily;
import org.labkey.test.categories.Specimen;
import org.labkey.test.util.StudyHelper;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * CreateVialsTest also uses the specimen merge feature.
 */
@Category({Daily.class, Specimen.class})
@BaseWebDriverTest.ClassTimeout(minutes = 7)
public class SpecimenMergeTest extends BaseWebDriverTest
{
    protected static final String PROJECT_NAME = "SpecimenMergeTest";
    protected static final String FOLDER_NAME = "My Study";

    protected static final File LAB19_SPECIMENS = StudyHelper.getSpecimenArchiveFile("lab19.specimens");
    protected static final File LAB20_SPECIMENS = StudyHelper.getSpecimenArchiveFile("lab20.specimens");
    protected static final File LAB21_SPECIMENS = StudyHelper.getSpecimenArchiveFile("lab21.specimens");

    protected static final File SPECIMEN_TEMP_DIR = StudyHelper.getStudyTempDir();
    protected int pipelineJobCount = 3;

    protected final String _studyDataRoot = StudyHelper.getStudySubfolderPath();

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
        File tempDir = SPECIMEN_TEMP_DIR;
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
            LAB19_SPECIMENS,
            LAB20_SPECIMENS,
            LAB21_SPECIMENS
        };
        SpecimenImporter importer = new SpecimenImporter(new File(_studyDataRoot), archives, SPECIMEN_TEMP_DIR, FOLDER_NAME, pipelineJobCount);
        importer.setExpectError(true);
        importer.importAndWaitForComplete();

        // Check there was an error in the specimen merge.
        clickAndWait(Locator.linkWithText("ERROR"));
        assertTextPresent("lab20", "Conflicting specimens found for GlobalUniqueId 'AAA07XK5-02'");
        checkExpectedErrors(2);
    }

    protected void setUpSteps()
    {
        _containerHelper.createProject(PROJECT_NAME, null);
        _containerHelper.createSubfolder(PROJECT_NAME, PROJECT_NAME, FOLDER_NAME, "Study", null);
        _containerHelper.enableModule("Specimen");
        clickButton("Create Study");
        clickButton("Create Study");

        setPipelineRoot(_studyDataRoot);
        clickFolder("My Study");
        clickAndWait(Locator.linkWithText("Manage Files"));
    }
}
