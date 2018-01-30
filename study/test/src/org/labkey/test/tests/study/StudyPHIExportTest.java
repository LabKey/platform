/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.junit.Assert;
import org.junit.experimental.categories.Category;
import org.labkey.test.components.PropertiesEditor.PhiSelectType;
import org.labkey.test.Locator;
import org.labkey.test.SortDirection;
import org.labkey.test.categories.DailyC;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

@Category({DailyC.class})
public class StudyPHIExportTest extends StudyExportTest
{
    private String idPreface = "P!@#$%^&*(";
    private int idLength = 7;
    private Map<String,String> _originalFirstMouseStats;

    @Override
    protected void doCreateSteps()
    {
        createStudyManually();

        _originalFirstMouseStats = getFirstMouseStats();
        setParticipantIdPreface(idPreface, idLength);

        exportStudy(true, false, PhiSelectType.NotPHI, true, true, false, null);
    }

    protected void setParticipantIdPreface(String idPreface, int idLength)
    {
        clickTab("Manage");
        clickAndWait(Locator.linkContainingText("Manage Alternate"));
        _extHelper.setExtFormElementByLabel("Prefix", idPreface);
        setFormElement(Locator.name("numberOfDigits"), "" + idLength);
        clickButton("Change Alternate IDs", 0);
        waitForText("Are you sure you want to change all Alternate IDs?");
        clickButton("OK", 0);
        sleep(1000);
        waitForText("Changing Alternate IDs is complete");
        clickButton("OK", 0);
    }

    private void verifyParticipantGroups(String originalID, String newID)
    {
        clickAndWait(Locator.linkWithText("Mice"));
        assertTextNotPresent(originalID);
        assertTextPresent(newID);

        // not in any group only appears if there are participants not in any of the groups in a category
        assertTextPresent("Group 1", "Group 2");
        assertTextNotPresent("Not in any cohort");

        _ext4Helper.uncheckGridRowCheckbox("Group 1");
        _ext4Helper.uncheckGridRowCheckbox("Group 2");

        waitForText("No matching Mice");


        _ext4Helper.clickParticipantFilterGridRowText("Group 1", 0);
        waitForText("Found 10 mice of 25");
        assertElementPresent(Locator.xpath("//a[contains(@href, 'participant.view')]"), 10);

        log("verify sorting by groups works properly");
        goToDatasets();
        clickAndWait(Locator.linkContainingText("LLS-2"));
        DataRegionTable drt = new DataRegionTable( "Dataset", this);
        assertEquals("unexpected number of rows on initial viewing", 5, drt.getDataRowCount());
        drt.clickHeaderMenu("Groups", true, "Cohorts", "Group 1");

        assertEquals("unexpected number of rows for group 1", 3, drt.getDataRowCount());
        drt.clickHeaderMenu("Groups", true, "Cohorts", "Group 2");
        assertEquals("unexpected number of rows for cohort 2", 2, drt.getDataRowCount());
    }

    private void verifyStatsDoNotMatch(Map originalFirstMouseStats, Map alteredFirstMouseStats)
    {
        for(String columnName : defaultStatsToCollect)
        {
            assertNotSame(originalFirstMouseStats.get(columnName), alteredFirstMouseStats.get(columnName));
        }
    }

    private void verifyStatsMatch(Map originalFirstMouseStats, Map alteredFirstMouseStats)
    {
        for(String columnName : defaultStatsToCollect)
        {
            assertEquals(originalFirstMouseStats.get(columnName), alteredFirstMouseStats.get(columnName));
        }
    }

    @LogMethod
    private void importAlteredStudy()
    {
        clickButton("Import Study");
        clickButton("Use Pipeline");
        _fileBrowserHelper.selectFileBrowserItem("export/");
        Locator.XPathLocator fileRow = Locator.tag("tr").withClass("x4-grid-data-row").withAttributeContaining("data-recordid", "My Study_");
        waitForElement(fileRow);
        int exportCount = getElementCount(fileRow);
        fileRow = fileRow.index(exportCount - 1); // get most recent export
        waitForElement(fileRow);
        click(fileRow);

        _fileBrowserHelper.selectImportDataAction("Import Study");
        clickButton("Start Import"); // Validate queries page
    }

    @Override
    protected void doVerifySteps()
    {
        verifyImportingAlternateIds();

        clickFolder(getFolderName());
        deleteStudy();
        importAlteredStudy();
        waitForPipelineJobsToComplete(2, "study import", false);

        Map<String,String> alteredFirstMouseStats = getFirstMouseStats();
        assertTrue(alteredFirstMouseStats.get("Mouse Id").startsWith(idPreface));
        assertEquals(idPreface.length() + idLength, alteredFirstMouseStats.get("Mouse Id").length());
        DataRegionTable drt = new DataRegionTable( "Dataset", this);
        /* DOB doesn't change because it's a text field, not a true date.
           since it's the most unique thing on the page, we can use it to see a specific user and verify that
           the date fields did change
         */
        drt.setSort("DEMbdt", SortDirection.ASC);   // bring our record into view
        assertNotSame("2005-01-01", drt.getDataAsText(drt.getRow("1.Date of Birth", "3/6/1965"), "Contact Date"));
        verifyStatsDoNotMatch(_originalFirstMouseStats, alteredFirstMouseStats);
        verifyParticipantGroups(_originalFirstMouseStats.get("Mouse Id"), alteredFirstMouseStats.get("Mouse Id"));

        clickFolder(getFolderName());
        deleteStudy();
        importAlteredStudy();
        waitForPipelineJobsToComplete(3, "Study reimport", false);

        Map reimportedFirstMouseStats = getFirstMouseStats();
        verifyStatsMatch(alteredFirstMouseStats, reimportedFirstMouseStats);

        log("Verify second export and clinic masking");

        startSpecimenImport(4, SPECIMEN_ARCHIVE_A);
        waitForPipelineJobsToComplete(4, "Specimen import", false);
        exportStudy(true, false, PhiSelectType.NotPHI, true, true, true, null);

        clickFolder(getFolderName());
        deleteStudy();
        importAlteredStudy();
        waitForPipelineJobsToComplete(5, "Study reimport with specimens", false);

        verifyMaskedClinics(8);
    }

    private void goToDatasets()
    {
        clickFolder(getFolderName());
        clickAndWait(Locator.linkContainingText("datasets"));
    }

    protected  void verifyMaskedClinics(int clinicCount)
    {
        List<String> nonClinics = new ArrayList<>();

        goToSchemaBrowser();
        selectQuery("study", "Location");
        waitAndClickAndWait(Locator.linkWithText("view data"));
        DataRegionTable query = new DataRegionTable("query", this);
        Assert.assertTrue("Lab Code column should not be in default view", query.getColumnIndex("LabwareLabCode") == -1);
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addColumn("LabwareLabCode");
        _customizeViewsHelper.applyCustomView();

        query = new DataRegionTable("query", this); // reset column index cache
        int labelCol = query.getColumnIndex("Label");
        int labCodeCol = query.getColumnIndex("LabwareLabCode");
        int clinicCol = query.getColumnIndex("Clinic");
        int rowCount = query.getDataRowCount();
        int foundClinics = 0;
        for (int i = 0; i < rowCount; i++)
        {
            if (query.getDataAsText(i, clinicCol).equals("true"))
            {
                foundClinics++;
                assertEquals("Clinic Location name was not masked", "Clinic", query.getDataAsText(i, labelCol));
                assertEquals("Clinic Labware Lab Code was not masked", "", query.getDataAsText(i, labCodeCol).trim());
            }
            else // non-clinic
            {
                assertNotEquals("Non-clinic Location name was masked", "Clinic", query.getDataAsText(i, labelCol));
                nonClinics.add(query.getDataAsText(i, labelCol));
            }
        }
        assertEquals("Unexpected number of clinics", clinicCount, foundClinics);

// Redundent because schema browser "view data" of study/locations and "manage Locations" link to the same table.
//
//        clickTab("Manage");
//        clickAndWait(Locator.linkWithText("Manage Locations"));
//        foundClinics = 0;
//        rowCount = getElementCount(Locator.xpath("id('manageLocationsTable')/tbody/tr"));
//        for (int i = 2; i <= rowCount - 2; i++) // skip header row; Stop before Add Location row & Save/Cancel button row
//        {
//            Locator.XPathLocator rowLoc = Locator.xpath("id('manageLocationsTable')/tbody/tr["+i+"]");
//            String locId = getText(rowLoc.append("/td[2]"));
//            String locName = getFormElement(rowLoc.append("/td/input[@name='labels']"));
//            String locTypes = getText(rowLoc.append("/td[5]"));
//            if (locTypes.contains("Clinic"))
//            {
//                assertTrue("Clinic Location name not masked: " + locId, locName.equals("Clinic"));
//                foundClinics++;
//            }
//            else
//            {
//                assertFalse("Non-Clinic Location name masked. Types: " + locId, locName.equals("Clinic"));
//                nonClinics.add(locName);
//            }
//        }
//        assertEquals("Unexpected number of clinics", clinicCount, foundClinics);

        clickTab("Specimen Data");
        sleep(2000); // the link moves while the specimen search form finishes layout
        waitAndClickAndWait(Locator.linkWithText("Blood (Whole)"));
        DataRegionTable vialsTable = new DataRegionTable("SpecimenDetail", this);
        List<String> procLocs = vialsTable.getColumnDataAsText("Processing Location");
        procLocs.remove(procLocs.size() - 1); // Skip aggregate row
        for (String procLoc : procLocs)
        {
            assertTrue("Processing Locations was not masked", procLoc.equals("Clinic") || nonClinics.contains(procLoc));
        }
        List<String> siteNames = vialsTable.getColumnDataAsText("Site Name");
        siteNames.remove(siteNames.size() - 1); // Skip aggregate row
        for (String siteName : siteNames)
        {
            assertTrue("Site Name was not masked", siteName.equals("Clinic") || siteName.equals("In Transit"));
        }
    }

    @Override
    public void runApiTests()
    {

    }

    String[] defaultStatsToCollect = {"Mouse Id", "Contact Date"};
    //ID, DOB
    public Map<String, String> getFirstMouseStats()
    {
        goToDatasets();
        clickAndWait(Locator.linkContainingText("DEM-1"));
        DataRegionTable drt = new DataRegionTable("Dataset", this);
        Map stats = new HashMap();


        for(int i = 0; i <defaultStatsToCollect.length; i++)
        {
            stats.put(defaultStatsToCollect[i], drt.getDataAsText(0, defaultStatsToCollect[i]));
        }

        return stats;
    }

    private static final String BAD_ALTERNATEID_MAPPING =
            "ParticipantId\tAlternateId\tDateOffset\n" +
                    "999320582\tNEWALT_32\t0\n" +
                    "999320638\tNEWALT_32\t1";

    private static final String ALTERNATEID_MAPPING =
            "ParticipantId\tAlternateId\tDateOffset\n" +
                    "999320582\tNEWALT_32\t0\n" +
                    "999320638\tNEWALT_33\t1";

    private static final String BAD_ALTERNATEID_MAPPING_2 =
            "ParticipantId\tAlternateId\tDateOffset\n" +
                    "999320533\tNEWALT_32\t0\n" +
                    "999320638\tNEWALT_33\t1";

    private static final String BAD_ALTERNATEID_MAPPING_3 =
            "ParticipantId\tAlternateId\tDateOffset\n" +
                    "999320582\n" +
                    "999320638\tNEWALT_13\t1";

    private static final String BAD_ALTERNATEID_MAPPING_4 =
                    "999320582\tNEWALT_12\t0\n" +
                    "999320638\tNEWALT_13\t1";

    private static final String ALTERNATEID_MAPPING_2 =
            "AlternateId\tParticipantId\tDateOffset\n" +
                    "NEWALT_32AB9\t999320582\t0\n" +
                    "NEWALT_333Q\t999320638\t1";

    @LogMethod
    private void verifyImportingAlternateIds()
    {
        goToManageStudy();
        clickAndWait(Locator.linkContainingText("Manage Alternate"));
        clickButton("Import");
        waitForElement(Locator.xpath("//textarea[@id='tsv3']"));
        assertTextPresent("Export Participant Transforms");
        setFormElement(Locator.xpath("//textarea[@id='tsv3']"), BAD_ALTERNATEID_MAPPING);
        clickButton("Submit", "Two participants may not share the same Alternate ID.");

        setFormElement(Locator.xpath("//textarea[@id='tsv3']"), ALTERNATEID_MAPPING);
        clickButton("Submit");

        // Test that ids actually got changed
        clickButton("Import");
        waitForElement(Locator.xpath("//textarea[@id='tsv3']"));
        setFormElement(Locator.xpath("//textarea[@id='tsv3']"), BAD_ALTERNATEID_MAPPING_2);
        clickButton("Submit", "Two participants may not share the same Alternate ID.");

        // Test input lacking all columns
        setFormElement(Locator.xpath("//textarea[@id='tsv3']"), BAD_ALTERNATEID_MAPPING_3);
        clickButton("Submit", "Either AlternateId or DateOffset must be specified.");

        // Test input without header row
        setFormElement(Locator.xpath("//textarea[@id='tsv3']"), BAD_ALTERNATEID_MAPPING_4);
        clickButton("Submit", "The header row must contain ParticipantId and either AlternateId, DateOffset or both.");

        // Good input with different column order
        setFormElement(Locator.xpath("//textarea[@id='tsv3']"), ALTERNATEID_MAPPING_2);
        clickButton("Submit");

        assertTextPresent("Manage Alternate", "Aliases");
        clickButton("Done");
    }
}
