/*
 * Copyright (c) 2014-2015 LabKey Corporation
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

import org.apache.commons.collections15.Bag;
import org.apache.commons.collections15.bag.HashBag;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Study;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.WikiHelper;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@Category({DailyB.class, Study.class})
public class StudyVisitTagTest extends StudyBaseTest
{
    protected final String VISIT_TAG_QWP_TITLE = "VisitTag";
    protected final String VISIT_TAG_MAP_QWP_TITLE = "VisitTagMap";
    protected final String PARENT_FOLDER_STUDY = "VisitTagsStarter";
    protected final String STUDY_TEMPLATE = "StudyAxistTestTemplate";
    protected final String DATE_FOLDER_STUDY1 = "StudyAxisTest1";
    protected final String DATE_FOLDER_STUDY2 = "StudyAxisTest2";
    protected final String DATE_FOLDER_STUDY3 = "StudyAxisTest3";
    protected final String DATE_FOLDER_STUDY4 = "StudyAxisTest4";
    protected final String DATE_FOLDER_STUDY5 = "StudyAxisTest5";
    protected final String DATE_FOLDER_STUDY6 = "StudyAxisTest6";
    protected final String DATE_FOLDER_STUDY7 = "StudyAxisTest11";
    protected final String DATE_FOLDER_STUDY8 = "StudyAxisTest12";
    protected final String VISIT_FOLDER_STUDY1 = "StudyAxisTestA";
    protected final String VISIT_FOLDER_STUDY2 = "StudyAxisTestB";
    protected final String VISIT_FOLDER_STUDY3 = "StudyAxisTestC";
    protected final String VISIT_FOLDER_STUDY4 = "StudyAxisTestD";
    protected final String VISIT_FOLDER_STUDY5 = "StudyAxisTestE";
    protected final String VISIT_FOLDER_STUDY6 = "StudyAxisTestF";
    protected final String VISIT_FOLDER_STUDY7 = "StudyAxisTestG";
    protected final String VISIT_FOLDER_STUDY8 = "StudyAxisTestH";
    protected final String[] DATE_BASED_STUDIES = {DATE_FOLDER_STUDY1}; //, DATE_FOLDER_STUDY2, DATE_FOLDER_STUDY3, DATE_FOLDER_STUDY4, DATE_FOLDER_STUDY7, DATE_FOLDER_STUDY8};
    protected final String[] SINGLE_USE_TAG_ERRORS = {DATE_FOLDER_STUDY5}; //, DATE_FOLDER_STUDY6};
    protected final String[] VISIT_BASED_STUDIES = {VISIT_FOLDER_STUDY1}; //, VISIT_FOLDER_STUDY2, VISIT_FOLDER_STUDY3, VISIT_FOLDER_STUDY4, VISIT_FOLDER_STUDY5, VISIT_FOLDER_STUDY6, VISIT_FOLDER_STUDY7, VISIT_FOLDER_STUDY8};
    protected final String WIKIPAGE_NAME = "VisitTagGetDataAPITest";
    protected final String TEST_DATA_API_PATH = "server/test/data/api";
    //TODO: placeholder, need to create a new test page with appropriate js to test getData api in this context
    protected final String TEST_DATA_API_CONTENT = "/getDataVisitTest.html";
    //private Map<String, String> _visitTagMaps = new HashMap<>();
    private final PortalHelper _portalHelper = new PortalHelper(this);
    private final PortalHelper portalHelper = new PortalHelper(this);
    private final WikiHelper wikiHelper = new WikiHelper(this);

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected void doCreateSteps()
    {
        doCleanup(false);
        initializeFolder();
        setPipelineRoot(getStudySampleDataPath() + "VisitTags");
        importStudies();
    }

    @Override
    protected void initializeFolder()
    {
        _containerHelper.createProject(getProjectName(), "Study");
        for(String Study : DATE_BASED_STUDIES)
        {
            _containerHelper.createSubfolder(getProjectName(), getProjectName(), Study, "Study", null, true);
        }
        for(String Study : VISIT_BASED_STUDIES)
        {
            _containerHelper.createSubfolder(getProjectName(), getProjectName(), Study, "Study", null, true);
        }
        for(String Study : SINGLE_USE_TAG_ERRORS)
        {
            _containerHelper.createSubfolder(getProjectName(), getProjectName(), Study, "Study", null, true);
        }
    }

    protected void importStudies()
    {
        String visitTagsPath = TestFileUtils.getSampledataPath() + "/VisitTags";
        goToProjectHome();
        startImportStudyFromZip(new File(visitTagsPath, PARENT_FOLDER_STUDY + ".folder.zip"), false, false);
        goToProjectHome();
        addVisitTagAndTagMapQWP();
        for(String Study : DATE_BASED_STUDIES)
        {
            clickFolder(Study);
            startImportStudyFromZip(new File(visitTagsPath, Study + ".folder.zip"), false, false);
            waitForPipelineJobsToComplete(1, "Study import", false);
            clickFolder(Study);
            addVisitTagAndTagMapQWP();
//            setupAPITestWiki();
        }
        for(String Study : VISIT_BASED_STUDIES)
        {
            clickFolder(Study);
            startImportStudyFromZip(new File(visitTagsPath, Study + ".folder.zip"), false, false);
            waitForPipelineJobsToComplete(1, "Study import", false);
            clickFolder(Study);
            addVisitTagAndTagMapQWP();
//            setupAPITestWiki();
        }
        for(String Study : SINGLE_USE_TAG_ERRORS)
        {
            clickFolder(Study);
            startImportStudyFromZip(new File(visitTagsPath, Study + ".folder.zip"), false, false);
            waitForPipelineJobsToComplete(1, "Study import", true);
            checkExpectedErrors(1);
            clickFolder(Study);
            addVisitTagAndTagMapQWP();
//            setupAPITestWiki();
        }

    }

    @Override
    protected void doVerifySteps() throws Exception
    {
        final List<String> VISIT_TAG_NAMES = Arrays.asList("day0", "finalvaccination", "finalvisit", "firstvaccination", "notsingleuse", "peakimmunogenicity");
        final List<String> VISIT_TAG_CAPTIONS = Arrays.asList("Day 0 (meaning varies)", "Final Vaccination", "Final visit", "First Vaccination", "Not Single Use Tag", "Predicted peak immunogenicity visit");
        Bag<List<String>> expectedRows = new HashBag<>(DataRegionTable.collateColumnsIntoRows(VISIT_TAG_NAMES, VISIT_TAG_CAPTIONS));

        goToProjectHome();
        DataRegionTable visitTags = getVisitTagTable();
        Bag<List<String>> actualRows = new HashBag<>(visitTags.getRows("Name", "Caption"));
        assertEquals("Wrong Visit Tag Data", expectedRows, actualRows);

        final List<String> VISIT_TAG_MAP_TAGS = Arrays.asList("Day 0 (meaning varies)", "First Vaccination", "Final Vaccination", "First Vaccination", "Final Vaccination", "Final visit");
        final List<String> VISIT_TAG_MAP_VISITS = Arrays.asList("Visit1", "Visit2", "Visit3", "Visit3", "Visit4", "Visit5");
        final List<String> VISIT_TAG_MAP_COHORTS = Arrays.asList(" ", "Positive", "Negative", "Negative", "Positive", " ");
        expectedRows = new HashBag<>(DataRegionTable.collateColumnsIntoRows(VISIT_TAG_MAP_TAGS, VISIT_TAG_MAP_VISITS, VISIT_TAG_MAP_COHORTS));

        DataRegionTable visitTagMaps = getVisitTagMapTable();
        actualRows = new HashBag<>(visitTagMaps.getRows("VisitTag", "Visit", "Cohort"));
        assertEquals("Wrong Visit Tag Map Data", expectedRows, actualRows);

        verifyInsertEditVisitTags();
    }

    protected String getProjectName()
    {
        return "VisitTagStudyVerifyProject";
    }

    protected void addVisitTagAndTagMapQWP()
    {
        _portalHelper.addQueryWebPart(VISIT_TAG_QWP_TITLE, "study", "VisitTag", null);
        _portalHelper.addQueryWebPart(VISIT_TAG_MAP_QWP_TITLE, "study", "VisitTagMap", null);
    }

    protected void setupAPITestWiki()
    {
        portalHelper.addWebPart("Wiki");
        wikiHelper.createNewWikiPage("HTML");
        setFormElement(Locator.name("name"), WIKIPAGE_NAME);
        setFormElement(Locator.name("title"), WIKIPAGE_NAME);
        wikiHelper.setWikiBody(TestFileUtils.getFileContents(TEST_DATA_API_PATH + "/getDataVisitTest.html"));
        wikiHelper.saveWikiPage();
        waitForText(WAIT_FOR_JAVASCRIPT, "Current Config");
    }

    protected DataRegionTable getVisitTagTable()
    {
        return new DataRegionTable(DataRegionTable.getTableNameByTitle("VisitTag", this), this);
    }

    protected DataRegionTable getVisitTagMapTable()
    {
        return new DataRegionTable(DataRegionTable.getTableNameByTitle("VisitTagMap", this), this);
    }

    protected void verifyInsertEditVisitTags()
    {
        goToProjectHome();
        insertVisitTag(VISIT_TAG_QWP_TITLE, new VisitTag("FollowUp1", "Follow Up 1", "", false));
        insertVisitTagMap(VISIT_TAG_MAP_QWP_TITLE, new VisitTagMap("FollowUp1", "Visit5", null));

        insertVisitTagMap(VISIT_TAG_MAP_QWP_TITLE, new VisitTagMap("FollowUp1", "Visit5", null));
        assertTextPresent("VisitTagMap may contain only one row for each (VisitTag, Visit, Cohort) combination.");
        clickButton("Cancel");
    }
}
