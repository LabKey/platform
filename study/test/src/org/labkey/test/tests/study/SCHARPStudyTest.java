/*
 * Copyright (c) 2016-2026 LabKey Corporation
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
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.Daily;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 6)
public class SCHARPStudyTest extends BaseWebDriverTest
{
    public static final String PROJECT_NAME="SCHARP Study Test";

    private final File _studyDir = TestFileUtils.getSampleData("studies/studyshell");

    @Test
    public void testSteps() throws IOException
    {
        log("creating project...");
        _containerHelper.createProject(PROJECT_NAME, "Study");

        clickProject(PROJECT_NAME);
        importStudy();

        log("Study imported and queries validated successfully.");
    }

    @LogMethod
    protected void importStudy() throws IOException
    {
        log("Importing study from " + _studyDir + "...");
        importStudyFromZip(new ZipUtil(_studyDir).tempZip());

        waitForPipelineJobsToComplete(1, "Study import", false);

        clickProject(PROJECT_NAME);
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
