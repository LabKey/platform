/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.test.tests.upgrade;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locators;
import org.labkey.test.pages.ConfigureReportsAndScriptsPage;
import org.labkey.test.pages.ConfigureReportsAndScriptsPage.EngineType;
import org.labkey.test.pages.admin.RConfigurationPage;
import org.labkey.test.pages.mothership.EditUpgradeMessagePage;
import org.labkey.test.pages.query.ExecuteQueryPage;
import org.labkey.test.util.RReportHelper;

import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({})
public class EncryptionKeyUpgradeTest extends BaseUpgradeTest
{
    private static final String DUMMY_R_SERVE = "Dummy RServe";

    @Override
    protected void doCleanup(boolean afterTest)
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    @Override
    protected void doSetup()
    {
        ConfigureReportsAndScriptsPage configureReportsAndScriptsPage = ConfigureReportsAndScriptsPage.beginAt(this);
        configureReportsAndScriptsPage.deleteEnginesFromList(List.of(DUMMY_R_SERVE));

        // Create an R engine with a password (which will be encrypted)
        ConfigureReportsAndScriptsPage.EngineConfig config = new ConfigureReportsAndScriptsPage.RServeEngineConfig()
                .setPassword("password")
                .setName(DUMMY_R_SERVE);
        configureReportsAndScriptsPage.addEngine(EngineType.REMOTE_R, config);

        _containerHelper.createProject(getProjectName(), null);
        RConfigurationPage.beginAt(this, getProjectName())
                .setEngineOverrides(DUMMY_R_SERVE, DUMMY_R_SERVE)
                .save();

        // Set StatusCake api key
        EditUpgradeMessagePage.beginAt(this)
                .setStatusCakeApiKey("password")
                .save();
    }

    @Test
    public void testDummyRemoteREngine()
    {
        // Trying to contact the R server will trigger an error if there is a problem with password encryption
        ExecuteQueryPage.beginAt(this, getProjectName(), "core", "containers").getDataRegion()
                .goToReport("Create R Report");
        new RReportHelper(this).clickReportTab();

        waitForElement(Locators.labkeyError.withText("Error executing command"));
        assertTextPresent("Could not connect to: 127.0.0.1:6311");

    }

    @Test
    public void testStatusCakeApiKey()
    {
        // Just loading this page can trigger an error if there was a problem with the encryption
        assertEquals("StatusCake API key input should be present but blank",
                "", EditUpgradeMessagePage.beginAt(this, null).getStatusCakeApiKey()); // Use the root container in case the '_mothership' project doesn't exist
    }

    @Override
    protected String getProjectName()
    {
        return "EncryptionKeyUpgradeTest Project";
    }

}
