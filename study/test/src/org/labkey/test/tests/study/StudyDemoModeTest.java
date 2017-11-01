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

import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.categories.DailyC;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.Crawler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;

@Category({DailyC.class})
public class StudyDemoModeTest extends StudyBaseTest
{
    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "StudyDemoMode Project";
    }

    @Override
    protected void doCreateSteps()
    {
        importStudy();
        startSpecimenImport(2);

        // wait for study and specimens to finish loading
        waitForPipelineJobsToComplete(2, "study import and specimens", false);
    }

    @Override
    protected void doVerifySteps()
    {
        enterDemoMode();
        clickFolder(getFolderName());
        DemoModeCrawler crawler = new DemoModeCrawler(this);
        crawler.crawlAllLinks();
        log("No participant IDs found");
        goToHome(); // Crawler endpoint in not deterministic
        leaveDemoMode();
    }

    // Verify that we're in demo mode, and that the messages and buttons are correct
    private void verifyInDemoMode()
    {
        assertTextPresent(
                "This study is currently in demo mode.",
                "Remember to hide your browser's address bar and status bar");
        assertButtonPresent("Leave Demo Mode");
        assertButtonPresent("Done");
    }

    // Verify that we're not in demo mode, and that the messages and buttons are correct
    private void verifyNotInDemoMode()
    {
        assertTextPresent(
                "This study is currently not in demo mode.",
                "Demo mode temporarily obscures mouse IDs in many pages of the study",
                "you should hide your browser's address bar and status bar");
        assertButtonPresent("Enter Demo Mode");
        assertButtonPresent("Done");
    }

    private void enterDemoMode()
    {
        navigateToFolder(getProjectName(), getFolderName());
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Demo Mode"));
        verifyNotInDemoMode();
        clickButton("Enter Demo Mode");
        verifyInDemoMode();
    }

    private void leaveDemoMode()
    {
        navigateToFolder(getProjectName(), getFolderName());
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Demo Mode"));
        verifyInDemoMode();
        clickButton("Leave Demo Mode");
        verifyNotInDemoMode();
    }


    private class DemoModeCrawler extends Crawler
    {
        private DemoModeCrawler(StudyDemoModeTest test)
        {
            // Crawl for max five minutes... which is way more than we currently need.
            super(test, 300000);
        }

        @Override
        protected List<String> getAdminControllers()
        {
            return Collections.emptyList();
        }

        @Override
        protected int getMaxDepth()
        {
            return 5;
        }

        @Override
        protected List<ControllerActionId> getDefaultExcludedActions()
        {
            List<ControllerActionId> list = super.getDefaultExcludedActions();
            list.add(new ControllerActionId("search", "search"));    // Search results page displays PTIDs
            list.add(new ControllerActionId("study", "datasetReport")); // Reports aren't expected to hide ptids
            list.add(new ControllerActionId("reports", "runReport")); // Reports aren't expected to hide ptids
            return list;
        }

        @Override
        // Override to check for visible PTIDs (can't just add to forbidden words, since PTIDs are still be present in links, etc.)
        protected void checkForForbiddenWords(String relativeURL)
        {
            // Check the standard forbidden words and fail if they're found
            super.checkForForbiddenWords(relativeURL);

            String bodyText = getBodyText();
            // Now look for PTIDs in the visible text of the page and fail if we find one
            for (String ptid : Arrays.asList("999320", "999321", "618005775"))
                assertFalse("Found visible ptid during demo mode", bodyText.contains(ptid));
        }
    }
}
