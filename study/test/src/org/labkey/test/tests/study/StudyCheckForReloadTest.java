/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import org.apache.commons.io.FileUtils;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.SimpleHttpRequest;
import org.labkey.test.util.SimpleHttpResponse;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class StudyCheckForReloadTest extends StudyBaseTest
{
    @Override
    @LogMethod
    protected void doCreateSteps()
    {
        log("Initializing project folder and importing study");
        initializeFolder();
        importStudyFromZip(TestFileUtils.getSampleData("studyreload/original.zip"));
    }

    @Override
    @LogMethod
    protected void doVerifySteps() throws IOException
    {
        log("Copy files from \".unzip\" folder into parent folder");
        File fileRoot = TestFileUtils.getDefaultFileRoot(getProjectName() + "/" + getFolderName());
        File unzipDir = new File(fileRoot, ".unzip");
        FileUtils.copyDirectory(unzipDir, fileRoot);

        log("Create studyload.txt file");
        File studyload = new File(fileRoot + "\\studyload.txt");
        studyload.createNewFile();

        log("Sending api request...");

        String reloadURL = WebTestHelper.buildURL("study", "StudyVerifyProject/My Study", "checkForReload");
        SimpleHttpRequest request = new SimpleHttpRequest(reloadURL);
        request.getResponse();

        log("Check reload request receives 200 response");
        SimpleHttpResponse response = request.getResponse();
        assertEquals(response.getResponseMessage(), 200, response.getResponseCode());

        log("Check reload is surfaced in pipeline UI");
        waitForPipelineJobsToComplete(2, "Study reload", false);
        waitForElement(Locator.linkContainingText("Study reload"));
    }
}
