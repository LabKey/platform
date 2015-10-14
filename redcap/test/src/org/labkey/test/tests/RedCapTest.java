/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.test.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.Locators;
import org.labkey.test.TestCredentials;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyA;
import org.labkey.test.components.study.StudyOverviewWebPart;
import org.labkey.test.credentials.ApiKey;
import org.labkey.test.pages.redcap.ConfigurePage;
import org.labkey.test.util.redcap.ConfigXmlBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.labkey.test.util.StudyHelper.TimepointType;

@Category({DailyA.class})
public class RedCapTest extends BaseWebDriverTest
{
    public static final String SURVEY_NAME_LONGITUDINAL = "Longitudinal";
    public static final String SURVEY_NAME_CLASSIC = "Classic";
    public static final String SURVEY_NAME_DATA_TYPES = "Data_Types";

    public static final String SERVER_CREDENTIALS_KEY = "REDCap";
    public static String REDCAP_HOST;
    public static Map<String, ApiKey> API_KEYS;

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @BeforeClass
    public static void setupProject() throws Exception
    {
        RedCapTest init = (RedCapTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup() throws IOException
    {
        REDCAP_HOST = TestCredentials.getServer(SERVER_CREDENTIALS_KEY).getHost();
        API_KEYS = new HashMap<>();
        for (ApiKey key : TestCredentials.getServer(SERVER_CREDENTIALS_KEY).getApiKeys())
        {
            API_KEYS.put(key.getName(), key);
        }
        _containerHelper.createProject(getProjectName(), null);
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void testConfiguration() throws Exception
    {
        final String projectName = SURVEY_NAME_LONGITUDINAL;
        final String token = API_KEYS.get(projectName).getToken();

        _containerHelper.createSubfolder(getProjectName(), getProjectName(), "testConfiguration", "Study", new String[]{"REDCap"});
        createDefaultStudy();
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage External Reloading"));
        clickAndWait(Locator.linkWithText("Configure REDCap"));

        ConfigXmlBuilder configBuilder = new ConfigXmlBuilder()
                .withProjects(new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, projectName, "subject_id")
                        .withDemographic(false)
                        .withMatchSubjectIdByLabel(true)
                        .withForms(new ConfigXmlBuilder.RedCapProjectForm("testName", "testDate", true)))
                .withDuplicateNamePolicy("dupPolicy")
                .withTimepointType("visit");

        ConfigurePage configPage = new ConfigurePage(this)
                .addToken("missingProject", token)
                .setConfigurationXml(configBuilder.build())
                .save()
                .assertFailure(String.format("The the project : %s exists in the configuration info, but there isn't an entry for that project in the connection section. Please add an entry before saving.", projectName))
                .setToken(0, projectName, token)
                .save()
                .assertSuccess();
    }

    @Test
    public void testLongitudinal()
    {
        final String folderName = "testLongitudinal";
        ConfigXmlBuilder configBuilder = new ConfigXmlBuilder()
                .withProjects(new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, SURVEY_NAME_LONGITUDINAL, "participant_id"))
                .withTimepointType("visit");;

        setupRedCapFolderAndStartLoad(folderName, TimepointType.VISIT, configBuilder.build(), API_KEYS.get(SURVEY_NAME_LONGITUDINAL));
        waitForPipelineJobsToComplete(1, "Import files", false);

        clickTab("Overview");
        StudyOverviewWebPart overview = new StudyOverviewWebPart(this);
        Assert.assertTrue("No participants", overview.getParticipantCount() > 0);

        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Visits"));

        List<String> expectedVisits = Arrays.asList("initial_data_arm_1", "week_1_arm_1", "week_2_arm_1", "week_3_arm_1", "week_4_arm_1", "final_data_arm_1");
        List<String> visits = getTexts(Locator.css("#visits tr:not(:first-child) > th").findElements(getDriver()));
        Assert.assertEquals("Wrong visits imported", expectedVisits, visits);
    }

    @Test
    public void testVisitBased()
    {
        final String folderName = "testVisitBased";
        ConfigXmlBuilder configBuilder = new ConfigXmlBuilder()
                .withProjects(new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, SURVEY_NAME_CLASSIC, "participant_id"))
                .withTimepointType("visit");

        setupRedCapFolderAndStartLoad(folderName, TimepointType.VISIT, configBuilder.build(), API_KEYS.get(SURVEY_NAME_CLASSIC));
        waitForPipelineJobsToComplete(1, "Import files", false);

        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Visits"));

        List<String> expectedVisits = Collections.singletonList("Timepoint 1");
        List<String> visits = getTexts(Locator.css("#visits tr:not(:first-child) > th").findElements(getDriver()));
        Assert.assertEquals("Wrong visits imported", expectedVisits, visits);
    }

    @Test //TODO: Insert more timepoints
    public void testDateBased() throws Exception
    {
        final String folderName = "testDateBased";
        ConfigXmlBuilder configBuilder = new ConfigXmlBuilder()
                .withProjects(new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, SURVEY_NAME_CLASSIC, "participant_id"))
                .withTimepointType("date");

        setupRedCapFolderAndStartLoad(folderName, TimepointType.DATE, configBuilder.build(), API_KEYS.get(SURVEY_NAME_CLASSIC));
        waitForPipelineJobsToComplete(1, "Import files", false);

        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Timepoints"));
        List<String> expectedVisits = Collections.singletonList("Day 0");
        List<String> visits = getTexts(Locator.css(".manage-visit-table tr:not(:first-child) > td:nth-child(2)").findElements(getDriver()));
        Assert.assertEquals("Wrong visits imported", expectedVisits, visits);
    }

    @Test
    public void testSeparateDemographicProject() throws Exception
    {
        final String folderName = "testSeparateDemographicProject";
        ConfigXmlBuilder configBuilder = new ConfigXmlBuilder()
                .withProjects(
                        new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, SURVEY_NAME_CLASSIC, "participant_id"),
                        new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, SURVEY_NAME_DATA_TYPES, "subject_id")
                                .withDemographic(true))
                .withTimepointType("visit");

        setupRedCapFolderAndStartLoad(folderName, TimepointType.VISIT, configBuilder.build(), API_KEYS.get(SURVEY_NAME_CLASSIC), API_KEYS.get(SURVEY_NAME_DATA_TYPES));
        waitForPipelineJobsToComplete(1, "Import files", false);

        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Datasets"));

        assertElementPresent(Locator.id("dataregion_datasets").append("//td[8]").withText("demographic"), 1); // Only one demographic dataset

        clickAndWait(Locator.linkWithText("datatypes"));
        assertElementPresent(Locator.tag("tr").
                withDescendant(Locator.tagWithClass("td", "labkey-form-label").containing("Demographic Data")).
                withDescendant(Locator.tagWithText("td", "true")));
    }

    @Test
    public void testMergeDuplicateNames() throws Exception
    {
        checkErrors();
        final String folderName = "testMergeDuplicateNames";
        ApiKey keyCopy = new ApiKey();
        String keyCopyName = SURVEY_NAME_CLASSIC + "2";
        keyCopy.setName(keyCopyName);
        keyCopy.setToken(API_KEYS.get(SURVEY_NAME_CLASSIC).getToken());
        ConfigXmlBuilder configBuilder = new ConfigXmlBuilder()
                .withProjects(
                        new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, SURVEY_NAME_CLASSIC, "participant_id"),
                        new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, keyCopyName, "participant_id")) // TODO: Create duplicate REDCap project
                .withTimepointType("visit")
                .withDuplicateNamePolicy("merge");

        setupRedCapFolderAndStartLoad(folderName, TimepointType.VISIT, configBuilder.build(), API_KEYS.get(SURVEY_NAME_CLASSIC), keyCopy);
        waitForPipelineJobsToComplete(1, "Import files", true);
        clickAndWait(Locator.linkWithText("ERROR")); // Error, because of self-merge
        assertElementPresent(Locators.labkeyError.containing("Only one row is allowed for each Participant/Visit"));
        resetErrors();
    }

    @Test
    public void testFailDuplicateNames() throws Exception
    {
        checkErrors();
        final String folderName = "testFailDuplicateNames";
        ApiKey keyCopy = new ApiKey();
        String keyCopyName = SURVEY_NAME_CLASSIC + "2";
        keyCopy.setName(keyCopyName);
        keyCopy.setToken(API_KEYS.get(SURVEY_NAME_CLASSIC).getToken());
        ConfigXmlBuilder configBuilder = new ConfigXmlBuilder()
                .withProjects(
                        new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, SURVEY_NAME_CLASSIC, "participant_id"),
                        new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, keyCopyName, "participant_id"))
                .withTimepointType("visit")
                .withDuplicateNamePolicy("fail");

        setupRedCapFolderAndStartLoad(folderName, TimepointType.VISIT, configBuilder.build(), API_KEYS.get(SURVEY_NAME_CLASSIC), keyCopy);
        waitForPipelineJobsToComplete(1, "Import files", true);
        clickAndWait(Locator.linkWithText("ERROR"));
        assertElementPresent(Locators.labkeyError.containing("Collection instrument name collision found"));
        resetErrors();
    }

    @Test
    public void testDataTypes() throws Exception
    {
        final String folderName = "testDataTypes";
        ConfigXmlBuilder configBuilder = new ConfigXmlBuilder()
                .withProjects(new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, SURVEY_NAME_DATA_TYPES, "record_id").withDemographic(true));

        setupRedCapFolderAndStartLoad(folderName, TimepointType.DATE, configBuilder.build(), API_KEYS.get(SURVEY_NAME_DATA_TYPES));

        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        clickAndWait(Locator.linkWithText("datatypes"));

        Map<String, String> expectedFieldTypes = new HashMap<>();
        expectedFieldTypes.put("date", "Date and Time");
        expectedFieldTypes.put("short_text", "Text (String)");
        expectedFieldTypes.put("paragraph_text", "Text (String)");
        expectedFieldTypes.put("integer", "Integer");
        expectedFieldTypes.put("number", "Number (Double)");
        expectedFieldTypes.put("calc_field", "Text (String)");
        expectedFieldTypes.put("yes_no", "True/False (Boolean)");
        expectedFieldTypes.put("drop_down", "Integer");
        expectedFieldTypes.put("radio", "Integer");
        expectedFieldTypes.put("slider", "Integer");
        expectedFieldTypes.put("file_upload", "Text (String)");
        expectedFieldTypes.put("multi_select", "Text (String)");

        Map<String, String> actualFieldTypes = new HashMap<>();
        for (Map.Entry<String, String> fieldType : new HashSet<>(expectedFieldTypes.entrySet()))
        {
            String actualType = getText(Locator.tag("tr").withPredicate(Locator.xpath("td[1]").withText(fieldType.getKey())).append("/td[3]"));
            if (!fieldType.getValue().equals(actualType))
                actualFieldTypes.put(fieldType.getKey(), actualType);
            else
                expectedFieldTypes.remove(fieldType.getKey());
        }

        Assert.assertEquals("Wrong data types", expectedFieldTypes, actualFieldTypes);

        clickTab("Participants");
        clickAndWait(Locator.linkWithText("2"));

        List<String> data = getTexts(Locator.css("table.labkey-data-region tr > td:nth-child(2)").findElements(getDriver()));
        Assert.assertEquals("UTF characters not imported correctly", "I\uff4e\uff54\u00ea\uff52\u043b\uff41\uff54\u00ef\u07c0\uff4e\u0251\uff4c\u00ed\uff5a\u00e4\uff54\u00ed\u00f2\uff4e", data.get(1)); // Messed up "Internationalization"
        Assert.assertEquals("Long text box not imported correctly. length", 560, data.get(2).length());
        Assert.assertEquals("Integer not imported correctly", -6, Integer.parseInt(data.get(3)));
        Assert.assertEquals("Double not imported correctly", 1.123, Double.parseDouble(data.get(4)), DELTA);
        Assert.assertEquals("Calculated field not imported correctly", -4.877, Double.parseDouble(data.get(5)), DELTA);
        Assert.assertEquals("Boolean not imported correctly", false, Boolean.parseBoolean(data.get(6)));
        Assert.assertEquals("Drop-down not imported correctly", 3, Integer.parseInt(data.get(7)));
        Assert.assertEquals("Radio selection not imported correctly", 3, Integer.parseInt(data.get(8)));
        Assert.assertEquals("Slider not imported correctly", 80, Integer.parseInt(data.get(9)));
        Assert.assertEquals("File Upload not imported correctly", "[document]", data.get(10));
        Assert.assertEquals("Multi-select not imported correctly", "Dinner,Lunch", data.get(11));
    }

    private void setupRedCapFolderAndStartLoad(String folderName, TimepointType timepointType, String configXml, ApiKey... apiKeys)
    {
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), folderName, "Study", new String[]{"REDCap"});
        _studyHelper.startCreateStudy().
                setTimepointType(timepointType).
                createStudy();

        ConfigurePage configPage = ConfigurePage.beginAt(this, String.join("/", getProjectName(), folderName));
        for (ApiKey apiKey : apiKeys)
        {
            configPage.addToken(apiKey.getName(), apiKey.getToken());
        }
        configPage
                .setConfigurationXml(configXml)
                .save()
                .assertSuccess()
                .reloadNow();
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "RedCapTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Collections.singletonList("redcap");
    }
}