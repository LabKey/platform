/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyC;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.StudyHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({DailyC.class})
public class StudyVisitTagTest extends StudyBaseTest
{
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

    private List<String> ALL_STUDIES = new ArrayList<>();

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "VisitTagStudyVerifyProject";
    }

    @Override
    protected void doCreateSteps()
    {
        ALL_STUDIES.addAll(Arrays.asList(DATE_BASED_STUDIES));
        ALL_STUDIES.addAll(Arrays.asList(VISIT_BASED_STUDIES));
        ALL_STUDIES.addAll(Arrays.asList(SINGLE_USE_TAG_ERRORS));

        doCleanup(false);
        initializeFolder();
        setPipelineRoot(StudyHelper.getStudySampleDataPath() + "VisitTags");
        importStudies();
    }

    @Override
    protected void initializeFolder()
    {
        _containerHelper.createProject(getProjectName(), "Study");
        for (String study : ALL_STUDIES)
        {
            _containerHelper.createSubfolder(getProjectName(), getProjectName(), study, "Study", null, true);
        }
    }

    protected void importStudies()
    {
        File visitTagsPath = TestFileUtils.getSampleData("VisitTags");
        goToProjectHome();
        startImportStudyFromZip(new File(visitTagsPath, PARENT_FOLDER_STUDY + ".folder.zip"), false, false);

        for (String study : DATE_BASED_STUDIES)
        {
            clickFolder(study);
            startImportStudyFromZip(new File(visitTagsPath, study + ".folder.zip"), false, false);
            waitForPipelineJobsToComplete(1, "Study import", false);
        }

        for (String study : VISIT_BASED_STUDIES)
        {
            clickFolder(study);
            startImportStudyFromZip(new File(visitTagsPath, study + ".folder.zip"), false, false);
            waitForPipelineJobsToComplete(1, "Study import", false);
        }

        for (String study : SINGLE_USE_TAG_ERRORS)
        {
            clickFolder(study);
            startImportStudyFromZip(new File(visitTagsPath, study + ".folder.zip"), false, false);
            waitForPipelineJobsToComplete(1, "Study import", true /* expect error */);
            checkExpectedErrors(1);
        }
    }

    @Override
    protected void doVerifySteps() throws Exception
    {
        final List<String> VISIT_TAG_NAMES = Arrays.asList("day0", "finalvaccination", "finalvisit", "firstvaccination", "notsingleuse", "peakimmunogenicity");
        final List<String> VISIT_TAG_CAPTIONS = Arrays.asList("Day 0 (meaning varies)", "Final Vaccination", "Final visit", "First Vaccination", "Not Single Use Tag", "Predicted peak immunogenicity visit");
        Bag<List<String>> expectedRows = new HashBag<>(DataRegionTable.collateColumnsIntoRows(VISIT_TAG_NAMES, VISIT_TAG_CAPTIONS));

        goToProjectHome();
        goToModule("Query");
        viewQueryData("study", "VisitTag");
        DataRegionTable visitTags = new DataRegionTable("query", this);
        Bag<List<String>> actualRows = new HashBag<>(visitTags.getRows("Name", "Caption"));
        assertEquals("Wrong Visit Tag Data", expectedRows, actualRows);

        final List<String> VISIT_TAG_MAP_TAGS = Arrays.asList("Day 0 (meaning varies)", "First Vaccination", "Final Vaccination", "First Vaccination", "Final Vaccination", "Final visit");
        final List<String> VISIT_TAG_MAP_VISITS = Arrays.asList("Visit1", "Visit2", "Visit3", "Visit3", "Visit4", "Visit5");
        final List<String> VISIT_TAG_MAP_COHORTS = Arrays.asList(" ", "Positive", "Negative", "Negative", "Positive", " ");
        expectedRows = new HashBag<>(DataRegionTable.collateColumnsIntoRows(VISIT_TAG_MAP_TAGS, VISIT_TAG_MAP_VISITS, VISIT_TAG_MAP_COHORTS));

        goToModule("Query");
        viewQueryData("study", "VisitTagMap");
        DataRegionTable visitTagMaps = new DataRegionTable("query", this);
        actualRows = new HashBag<>(visitTagMaps.getRows("VisitTag", "Visit", "Cohort"));
        assertEquals("Wrong Visit Tag Map Data", expectedRows, actualRows);

        // verify insert/edit of tags
        goToProjectHome();
        insertVisitTag(new VisitTag("FollowUp1", "Follow Up 1", "", false));
        insertVisitTagMap(new VisitTagMap("FollowUp1", "Visit5", null));
        insertVisitTagMap(new VisitTagMap("FollowUp1", "Visit5", null));
        assertTextPresent("VisitTagMap may contain only one row for each (VisitTag, Visit, Cohort) combination.");
        clickButton("Cancel");
    }

    private void insertVisitTag(VisitTag tag)
    {
        goToModule("Query");
        viewQueryData("study", "VisitTag");

        DataRegionTable.findDataRegion(this).clickInsertNewRow();
        waitForElement(Locator.input("quf_Name"));
        setFormElement(Locator.input("quf_Name"), tag.name);
        setFormElement(Locator.input("quf_Caption"), tag.caption);
        setFormElement(Locator.tagWithName("textarea", "quf_Description"), tag.description);
        if (tag.isSingleUse)
        {
            click(Locator.checkboxByName("quf_SingleUse"));
        }
        clickAndWait(Locator.linkWithSpan("Submit"));
    }

    private void insertVisitTagMap(VisitTagMap map)
    {
        goToModule("Query");
        viewQueryData("study", "VisitTagMap");

        DataRegionTable.findDataRegion(this).clickInsertNewRow();
        waitForElement(Locator.name("quf_VisitTag"));
        selectOptionByValue(Locator.name("quf_VisitTag"), map.visitTag);
        selectOptionByText(Locator.name("quf_Visit"), map.visit);
        if (null != map.cohort && !map.cohort.isEmpty())
            selectOptionByText(Locator.name("quf_Cohort"), map.cohort);
        clickAndWait(Locator.linkWithSpan("Submit"));
    }

    public class VisitTag
    {
        protected String name;
        protected String caption;
        protected String description;
        protected Boolean isSingleUse;

        public VisitTag(String name, String caption, String description, Boolean isSingleUse)
        {
            this.name = name;
            this.caption = caption;
            this.description = description;
            this.isSingleUse = isSingleUse;
        }
    }

    public class VisitTagMap
    {
        protected String visitTag;
        protected String visit;
        protected String cohort;

        public VisitTagMap(String visitTag, String visit, String cohort)
        {
            this.visitTag = visitTag;
            this.visit = visit;
            this.cohort = cohort;
        }
    }
}
