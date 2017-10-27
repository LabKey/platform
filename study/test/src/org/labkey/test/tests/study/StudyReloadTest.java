/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.labkey.test.categories.DailyC;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PipelineStatusTable;

@Category({DailyC.class})
public class StudyReloadTest extends StudyBaseTest
{
    @Override
    //disabled for 14569
    @LogMethod
    protected void doCreateSteps()
    {
        initializeFolder();
        importStudyFromZip(TestFileUtils.getSampleData("studyreload/original.zip"));
    }

    @Override
    @LogMethod
    protected void doVerifySteps()
    {
        reloadStudyFromZip(TestFileUtils.getSampleData("studyreload/edited.zip"));
        //query validation should have been run by default
        new PipelineStatusTable(this).clickStatusLink(0);
        checkQueryValidationInLog(true);
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("1 dataset"));
        clickAndWait(Locator.linkWithText("update_test"));
        assertTextPresent("id006", "additional_column", "1234566");
        //text that was present in original but removed in the update
        assertTextNotPresent("original_column_numeric");

        //verify skipping query validation during reload
        reloadStudyFromZip(TestFileUtils.getSampleData("studyreload/edited.zip"), false, 3);
        new PipelineStatusTable(this).clickStatusLink(0);
        checkQueryValidationInLog(false);
    }

    private void checkQueryValidationInLog(boolean expectQueryValidation)
    {
        if(expectQueryValidation)
        {
            assertTextNotPresent("Skipping query validation.");
            assertTextPresent("Validating all queries in all schemas...");
        }
        if(!expectQueryValidation)
        {
            assertTextPresent("Skipping query validation.");
            assertTextNotPresent("Validating all queries in all schemas...");
        }
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
