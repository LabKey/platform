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

import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyC;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.DataRegionTable;

@Category({DailyC.class})
public class StudySecurityTest extends StudyBaseTest
{
    protected static final String READER = "dsreader@studysecurity.test";
    protected static final String EDITOR = "dseditor@studysecurity.test";
    protected static final String LIMITED = "dslimited@studysecurity.test";
    protected static final String NONE = "dsnone@studysecurity.test";

    protected static final String GROUP_READERS = "Readers";
    protected static final String GROUP_EDITORS = "Editors";
    protected static final String GROUP_LIMITED = "The Limited";
    protected static final String GROUP_NONE = "No Access";

    protected enum GroupSetting
    {
        editAll("UPDATE"),
        readAll("READ"),
        perDataset("READOWN"),
        none("NONE");

        private String _radioValue;

        GroupSetting(String radioValue)
        {
            _radioValue = radioValue;
        }

        public String getRadioValue()
        {
            return _radioValue;
        }
    }

    protected enum PerDatasetPerm
    {
        Read,
        Edit
    }

    protected void doCreateSteps()
    {
        //start import--need to wait for completion after setting up security
        importStudy();

        _userHelper.createUser(READER);
        _userHelper.createUser(EDITOR);
        _userHelper.createUser(LIMITED);
        _userHelper.createUser(NONE);
        clickProject(getProjectName());
        importFolderFromZip(TestFileUtils.getSampleData("studies/StudySecurityProject.folder.zip"));

        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("Manage Files"));
        waitForPipelineJobsToComplete(1, "study import", false);
    }

    protected void doVerifySteps()
    {
        setupStudySecurity();

        //now verify that each group sees what it's supposed to see
        String[] all = new String[]{"CPF-1: Follow-up Chemistry Panel", "DEM-1: Demographics"};
        String[] none = new String[0];
        String[] limited = new String[]{"DEM-1: Demographics", "Types"};
        String[] unlimited = new String[]{"CPF-1: Follow-up Chemistry Panel"};

        //system admins (current user) should be able to edit everything and setup the pipeline
        verifyPerms(null, all, none, all, none, true);

        //GROUP_READERS should be able to see all datasets, but not edit anything
        //and not do anything with the pipeline
        verifyPerms(READER, all, none, none, all, false);

        //GROUP_EDITORS should be able to see all datasets and edit them
        //and import new data via the pipeline, but not set the pipeline path
        verifyPerms(EDITOR, all, none, all, none, false);

        //GROUP_LIMITED should be able to see only the few datasets we granted them
        //and not do anything with the pipeline
        verifyPerms(LIMITED, limited, unlimited, none, limited, false);

        //GROUP_NONE should not be able to see any datasets nor the pipeline
        verifyPerms(NONE, none, all, none, all, false);

        //revoke limited's permissions and verify
        adjustGroupDatasetPerms(GROUP_LIMITED, GroupSetting.none);
        clickFolder(getFolderName());
        verifyPerms(LIMITED, none, all, none, all, false);

        //reinstate read to limited to verify that per-dataset settings were preserved
        adjustGroupDatasetPerms(GROUP_LIMITED, GroupSetting.perDataset);
        verifyPerms(LIMITED, limited, unlimited, none, limited, false);

        //move editors to per-dataset and grant edit only one of the datasets
        String[] edit = new String[]{"Types"};
        String[] noEdit = new String[]{"DEM-1: Demographics"};
        adjustGroupDatasetPerms(GROUP_EDITORS, GroupSetting.perDataset, edit, PerDatasetPerm.Edit);
        verifyPerms(EDITOR, edit, noEdit, edit, noEdit, false);

        //reset to general edit
        adjustGroupDatasetPerms(GROUP_EDITORS, GroupSetting.editAll);
        verifyPerms(EDITOR, all, none, all, none, false);
    }

    protected void adjustGroupDatasetPerms(String groupName, GroupSetting setting)
    {
        adjustGroupDatasetPerms(groupName, setting, null, null);
    }

    protected void adjustGroupDatasetPerms(String groupName, GroupSetting setting, String[] datasets, PerDatasetPerm perm)
    {
        navigateToFolder(getProjectName(), getFolderName());
        enterStudySecurity();

        click(getRadioButtonLocator(groupName, setting));
        clickButton("Update");

        if (null != datasets && null != perm)
        {
            for (String dsName : datasets)
            {
                selectOptionByText(getPerDatasetSelect(dsName), perm.name());
            }
            clickButton("Save");
        }

        clickFolder(getFolderName());
    }

    protected Locator getPerDatasetSelect(String dsName)
    {
        return Locator.xpath("//form[@id='datasetSecurityForm']/table/tbody/tr/td[text()='" + dsName + "']/../td/select");
    }

    protected void verifyPerms(String userName, String[] dsCanRead, String[] dsCannotRead, String[] dsCanEdit, String[] dsCannotEdit, boolean canSetupPipeline)
    {
        if (null != userName)
            impersonate(userName);

        navigateToFolder(getProjectName(), getFolderName());

        if (canSetupPipeline)
            assertButtonPresent("Setup");
        else
            assertButtonNotPresent("Setup");

        for (String dsName : dsCanRead)
        {
            assertElementPresent(Locator.linkWithText(dsName));
        }

        for (String dsName : dsCannotRead)
        {
            assertElementNotPresent(Locator.linkWithText(dsName));
        }

        for (String dsName : dsCanEdit)
        {
            assertElementPresent(Locator.linkWithText(dsName));
            clickAndWait(Locator.linkWithText(dsName));
            assertTextPresent(dsName);
            assertElementPresent(DataRegionTable.updateLinkLocator());
            DataRegionTable dt = new DataRegionTable("Dataset", getDriver());
            dt.clickHeaderButton("Insert data");    // expand the menu
            assertTextPresent(DataRegionTable.getInsertNewButtonText(), DataRegionTable.getImportBulkDataText());
            dt.clickHeaderButton("Insert data");     //collapse it
            clickFolder(getFolderName());
        }

        for (String dsName : dsCannotEdit)
        {
            if (isElementPresent(Locator.linkWithText(dsName)))
            {
                clickAndWait(Locator.linkWithText(dsName));
                assertTextPresent(dsName);
                assertElementNotPresent(DataRegionTable.updateLinkLocator());
                assertElementNotPresent(Locator.lkButton("Insert data"));
                clickFolder(getFolderName());
            }
        }

        if (null != userName)
            stopImpersonating();
    }

    protected void setupStudySecurity()
    {
        //setup advanced dataset security
        enterStudySecurity();

        waitForElement(Locator.name("securityString"));
        selectOptionByValue(Locator.name("securityString"), "ADVANCED_WRITE");
        clickAndWait(Locator.lkButton("Update Type"));
        waitForElements(Locator.tagWithName("div", "webpart"), 3);

        //the radio buttons are named "group.<id>" and since we don't know the
        //group ids, we need to find them by name
        click(getRadioButtonLocator(GROUP_READERS, GroupSetting.readAll));
        click(getRadioButtonLocator(GROUP_EDITORS, GroupSetting.editAll));
        click(getRadioButtonLocator(GROUP_LIMITED, GroupSetting.perDataset));
        click(getRadioButtonLocator(GROUP_NONE, GroupSetting.none));
        clickButton("Update");

        //grant limited rights to read a couple of datasets
        selectOptionByText(Locator.name("dataset.1"), "Read");
        selectOptionByText(Locator.name("dataset.2"), "Read");
        clickButton("Save");

        clickFolder(getFolderName());
    }

    protected Locator getRadioButtonLocator(String groupName, GroupSetting setting)
    {
        //not sure why the radios are in TH elements, but they are...
        return Locator.xpath("//form[@id='groupUpdateForm']/table/tbody/tr/td[text()='"
                + groupName + "']/../th/input[@value='" + setting.getRadioValue() + "']");
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
        deleteUsersIfPresent(READER, EDITOR, LIMITED, NONE);
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
