/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.test.tests;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.admin.CreateProjectPage;
import org.labkey.test.pages.admin.SetFolderPermissionsPage;
import org.labkey.test.pages.admin.SetInitialFolderSettingsPage;
import org.labkey.test.util.PortalHelper;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@Category({Daily.class})
public class ProjectWizardTest extends BaseWebDriverTest
{
    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @BeforeClass
    public static void setupProject()
    {
        ProjectWizardTest init = getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        log("this test verifies the project creation wizard from the project menu");
    }

    @Before
    public void preTest() throws Exception
    {
        goToHome();
    }

    /**
     * regression coverage for https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=37736
     */
    @Test
    public void testCreateFromProjectMenu() throws Exception
    {
        CreateProjectPage createProjectPage = projectMenu()
                .navigateToCreateProjectPage();
        assertTrue("the [Create Project] wizard step is not correctly highlit",
                createProjectPage.isCurrentStepHighlit());

        SetFolderPermissionsPage setFolderPermissionsPage = createProjectPage
                .setFolderType("Collaboration")
                .setProjectName(getProjectName())
                .clickNext();
        assertTrue("the [Users / Permissions] wizard step is not correctly highlit",
                setFolderPermissionsPage.isCurrentStepHighlit());

        SetInitialFolderSettingsPage setInitialFolderSettingsPage = setFolderPermissionsPage
                .setMyUserOnly()
                .clickNext();
        assertTrue("the [Project Settings] wizard step is not correctly highlit",
                setInitialFolderSettingsPage.isCurrentStepHighlit());

        setInitialFolderSettingsPage
                .useDefaultLocation()
                .clickFinish();

        // confirm expected webparts in a collaboration project are present
        assertThat("WebParts for collaboration folder type.",
                new PortalHelper(getDriver()).getWebPartTitles(), hasItems("Subfolders"));
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "ProjectWizardTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList();
    }
}
