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

import org.labkey.api.util.FileUtil;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.SimpleHttpRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class StudyCheckForReloadTest extends StudyBaseTest
{
    @Override
    @LogMethod
    protected void doCreateSteps()
    {
        log("Initializing project folder and importing study");

        initializeFolder();
        importStudyFromZip(TestFileUtils.getSampleData("studyreload/original.zip"));

        log("Copy files from \"unzip\" folder into parent folder");

        String path = "\\build\\deploy\\files\\StudyVerifyProject\\My Study\\@files";
        String fullBasePath = new File(new File("").toURI()).getParent() + path;
        try
        {
            FileUtil.copyDirectory(Paths.get(fullBasePath + "\\unzip"), Paths.get(fullBasePath));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        log("Create studyload.txt file");

        File studyload = new File(fullBasePath + "\\studyload.txt");
        try
        {
            studyload.createNewFile();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    @LogMethod
    protected void doVerifySteps()
    {
        log("Sending api request...");

        String reloadURL = WebTestHelper.buildURL("study", "StudyVerifyProject/My Study", "checkForReload");
        SimpleHttpRequest request = new SimpleHttpRequest(reloadURL);
        try
        {
            request.getResponse();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        log("Check reload is surfaced in pipeline UI");

        waitForPipelineJobsToComplete(2, "Study reload", false);
        waitForElement(Locator.linkContainingText("Study reload"));
    }
}
