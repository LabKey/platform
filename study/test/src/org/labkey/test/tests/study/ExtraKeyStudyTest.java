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
import org.labkey.test.util.LoggedParam;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category({DailyC.class})
public class ExtraKeyStudyTest extends StudyBaseTest
{
    private static final File studyFolder = TestFileUtils.getSampleData("studies/ExtraKeyStudy");

    String[] datasets = {
            "P_One",
            "P_Two",

            "PV_One",
            "PV_Two",

            "PVInt_One",
            "PVInt_Two",
            "PVInt_Three",

            "PVString_One",
            "PVString_Two",

            "PVDouble_One",
            "PVDouble_Two",

            "PVDate_One",
            "PVDate_Two",

            "PVCode_One",
            "PVCode_Two"
    };

    int[] visibility = {
            Integer.parseInt("000000000000010", 2), // P_One
            Integer.parseInt("000000000000001", 2), // P_Two

            Integer.parseInt("000000000001011", 2), // PV_One
            Integer.parseInt("000000000000111", 2), // PV_Two

            Integer.parseInt("000000000101111", 2), // PVInt_One
            Integer.parseInt("000000000011111", 2), // PVInt_Two
            Integer.parseInt("000000000001111", 2), // PVInt_Three

            Integer.parseInt("000000100001111", 2), // PVString_One
            Integer.parseInt("000000010001111", 2), // PVString_Two

            // PVDouble_* auto-join is currently disabled.  See Issue 14860.
            Integer.parseInt("000000000001111", 2), // PVDouble_One
            Integer.parseInt("000000000001111", 2), // PVDouble_Two

            Integer.parseInt("001000000001111", 2), // PVDate_One
            Integer.parseInt("000100000001111", 2), // PVDate_Two

            Integer.parseInt("100000000001111", 2), // PVCode_One
            Integer.parseInt("010000000001111", 2), // PVCode_Two
    };

    @Override
    protected void doCreateSteps()
    {
        initializeFolder();
        initializePipeline(studyFolder.getAbsolutePath());

        importFolderFromPipeline("folder.xml");
    }

    @Override
    protected void doVerifySteps()
    {
        log("TODO");
        navigateToFolder(getProjectName(), getFolderName());
        clickAndWait(Locator.linkContainingText("datasets"));

        for (int i = 0; i < datasets.length; i++)
        {
            String datasetName = datasets[i];
            verifyColumnVisibility(datasetName, visibility[i]);
        }
    }

    @LogMethod
    private void verifyColumnVisibility(@LoggedParam String datasetName, int visibility)
    {
        pushLocation();
        log("** Verifying visibility of other datasets from " + datasetName);
        clickAndWait(Locator.linkContainingText(datasetName));
        _customizeViewsHelper.openCustomizeViewPanel();

        // Participant columns should be visible, old "Participant/DataSet" lookup should be hidden.
        assertTrue("PandaId/PandaId should be visible", _customizeViewsHelper.isColumnVisible("PandaId/PandaId"));
        assertFalse("PandaId/DataSet lookup should not be visible", _customizeViewsHelper.isColumnVisible("PandaId/DataSet"));

        // ParticipantVisit columns should be visible, old "Paricipant Visit/<dataset>" lookups should be hidden.
        assertTrue("Panda Visit/PandaId should be visible", _customizeViewsHelper.isColumnVisible("PandaVisit/PandaId"));
        assertTrue("Panda Visit/Visit should be visible", _customizeViewsHelper.isColumnVisible("PandaVisit/Visit"));
        assertFalse("Panda Visit/PV_One should not be visible", _customizeViewsHelper.isColumnVisible("PandaVisit/PV_One"));
        assertFalse("Panda Visit/PV_Two should not be visible", _customizeViewsHelper.isColumnVisible("PandaVisit/PV_Two"));

        // DataSets auto-join lookups
        for (int j = 0; j < datasets.length; j++)
        {
            String otherDataset = datasets[j];
            boolean visible = isBitSet(visibility, j);
            String lookup = "DataSets/" + otherDataset;
            log("** Checking " + lookup + " is " + (visible ? "" : "not ") + "visible from " + datasetName);
            assertEquals("Expected " + lookup + " to be " + (visible ? "" : "not ") + "visible from " + datasetName,
                    visible, _customizeViewsHelper.isColumnVisible(lookup));
        }

        popLocation();
    }

    private boolean isBitSet(int bits, int bit)
    {
        return (bits & (1 << bit)) != 0;
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
