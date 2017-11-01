/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyA;
import org.labkey.test.categories.Specimen;

import java.io.File;

@Category({DailyA.class, Specimen.class})
public class SpecimenReplaceTest extends SpecimenMergeTest
{
    protected static final String LAB_EDITED_SPECIMENS = "/sampledata/study/specimens/lab19edit.specimens";
    protected static final String LAB15_SPECIMENS = "/sampledata/study/specimens/lab15.specimens";
    protected static final String LAB20_SPECIMENS = "/sampledata/study/specimens/lab20.specimens";
    protected static final String LAB21_SPECIMENS = "/sampledata/study/specimens/lab21.specimens";

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
        SpecimenImporter importer = new SpecimenImporter(new File(_studyDataRoot), new File[] {new File(TestFileUtils.getLabKeyRoot(), LAB15_SPECIMENS)}, new File(TestFileUtils.getLabKeyRoot(), SPECIMEN_TEMP_DIR), FOLDER_NAME, ++pipelineJobCount);
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
        SpecimenImporter importer = new SpecimenImporter(new File(_studyDataRoot), new File[] {new File(TestFileUtils.getLabKeyRoot(), LAB_EDITED_SPECIMENS)}, new File(TestFileUtils.getLabKeyRoot(), SPECIMEN_TEMP_DIR), FOLDER_NAME, ++pipelineJobCount);
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
