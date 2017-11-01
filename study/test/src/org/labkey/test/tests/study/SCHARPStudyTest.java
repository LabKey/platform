/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.test.categories.DailyA;
import org.labkey.test.util.PostgresOnlyTest;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.fail;

@Category({DailyA.class})
public class SCHARPStudyTest extends BaseWebDriverTest implements PostgresOnlyTest
{
    public static final String PROJECT_NAME="SCHARP Study Test";

    private String _labkeyRoot = TestFileUtils.getLabKeyRoot();
    private String _pipelinePathMain = new File(_labkeyRoot, "/sampledata/study").getPath();
    private File _studyZipFile = TestFileUtils.getSampleData("studies/studyshell.zip");

    protected static class StatusChecker implements Supplier<Boolean>
    {
        private BaseWebDriverTest _test;
        private String _waitForMessage;
        private Locator _loc = Locator.id("vq-status");

        public StatusChecker(String waitForMessage, BaseWebDriverTest test)
        {
            _test = test;
            _waitForMessage = waitForMessage;
        }

        public Boolean get()
        {
            String curMessage = _test.getText(_loc);
            if (null == curMessage)
                fail("Can't get message in locator " + _loc.toString());
            return (curMessage.startsWith(_waitForMessage));
        }
    }

    @Test
    public void testSteps()
    {
        log("creating project...");
        _containerHelper.createProject(PROJECT_NAME, "Study");

        clickProject(PROJECT_NAME);
        log("importing study...");
        setupPipeline();
        importStudy();

        log("Study imported and queries validated successfully.");
    }

    protected void setupPipeline()
    {
        log("Setting pipeline root to " + _pipelinePathMain + "...");
        setPipelineRoot(_pipelinePathMain);
        assertTextPresent("The pipeline root was set");
        clickProject(PROJECT_NAME);
    }

    protected void importStudy()
    {
        log("Importing study from " + _studyZipFile + "...");
        clickButton("Import Study");
        setFormElement(Locator.name("folderZip"), _studyZipFile);
        clickButton("Import Study");
        assertTextNotPresent("This file does not appear to be a valid .zip file");

        waitForPipelineJobsToComplete(1, "Study import", false);

        clickProject(PROJECT_NAME);
    }

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
