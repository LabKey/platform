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
import org.labkey.test.categories.Daily;
import org.labkey.test.categories.Specimen;
import org.labkey.test.util.StudyHelper;

import java.io.File;

@Category({Daily.class, Specimen.class})
@BaseWebDriverTest.ClassTimeout(minutes = 6)
public class SpecimenReplaceTest extends SpecimenMergeTest
{
    protected static final File LAB_EDITED_SPECIMENS = StudyHelper.getSpecimenArchiveFile("lab19edit.specimens");
    protected static final File LAB15_SPECIMENS = StudyHelper.getSpecimenArchiveFile("lab15.specimens");
    protected static final File LAB20_SPECIMENS = StudyHelper.getSpecimenArchiveFile("lab20.specimens");
    protected static final File LAB21_SPECIMENS = StudyHelper.getSpecimenArchiveFile("lab21.specimens");

    @Override
    @Test
    public void testSteps()
    {
        setUpSteps();
        importFirstFileSet();

        verifyReplaceWithIdenticalFiles();
        verifyReplaceWithSlightlyModifiedData();
        verifyReplaceWithNewData();
    }

    private void verifyReplaceWithNewData()
    {
        SpecimenImporter importer = new SpecimenImporter(new File(_studyDataRoot), new File[] {LAB15_SPECIMENS}, SPECIMEN_TEMP_DIR, FOLDER_NAME, ++pipelineJobCount);
        importer.setExpectError(true);
        importer.importAndWaitForComplete();
        //go to individual vial list
        goToIndividualvialsDRT();
        assertElementPresent(Locator.paginationText(12));

        //entry for participant 999320812 have been replaced with 123123123
        assertTextPresent("999320812");
        assertTextNotPresent("123123123");
    }

    private void verifyReplaceWithSlightlyModifiedData()
    {
        SpecimenImporter importer = new SpecimenImporter(new File(_studyDataRoot), new File[] {LAB_EDITED_SPECIMENS}, SPECIMEN_TEMP_DIR, FOLDER_NAME, ++pipelineJobCount);
        importer.setExpectError(true);
        importer.importAndWaitForComplete();
        //go to individual vial list
        goToIndividualvialsDRT();
        assertElementPresent(Locator.paginationText(1, 100, 666));

        //entry for participant 999320812 have been replaced with 123123123
        assertTextNotPresent("999320812");
        assertTextPresent("123123123");
    }

    private void verifyReplaceWithIdenticalFiles()
    {
        pipelineJobCount += 3;
        importFirstFileSet();
        goToIndividualvialsDRT();

        assertElementPresent(Locator.paginationText(1, 100, 667));
    }

    private void goToIndividualvialsDRT()
    {
        clickTab("Specimen Data");
        waitAndClickAndWait(Locator.linkWithText("By Individual Vial"));
    }
}
