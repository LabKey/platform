package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.collections.CaseInsensitiveHashMap;
import org.labkey.remoteapi.domain.CreateDomainCommand;
import org.labkey.remoteapi.domain.DomainCommand;
import org.labkey.remoteapi.domain.DomainResponse;
import org.labkey.remoteapi.domain.SaveDomainCommand;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.DailyA;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@Category({DailyA.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class StudyDatasetDomainTest extends BaseWebDriverTest
{
    private final String DOMAIN_KIND = "StudyDatasetDate";
    private final String STUDY_DATASET_NAME = "vampireBloodLevels";

    private final String BLANK_PROPERTY_ERROR_MSG = "Name field must not be blank.";
    private final String DUPLICATE_FIELD_ERROR_MSG = "All property names must be unique. Duplicate found: bloodLevel.";

    private int NUM_ERRORS = 0;

    @Override
    protected @Nullable String getProjectName()
    {
        return "StudyDatasetDomainTest Project";
    }

    protected String getFolderName()
    {
        return "My Study Domain";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @BeforeClass
    public static void doSetup()
    {
        StudyDatasetDomainTest init = (StudyDatasetDomainTest)getCurrentTest();
        init.doCreateSteps();
    }

    private void doCreateSteps()
    {
        _containerHelper.createProject(getProjectName(), null);
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), getFolderName(), "Study", null, true);

        clickButton("Create Study");
        setFormElement(Locator.name("subjectNounSingular"), "Vampire");
        setFormElement(Locator.name("subjectNounPlural"), "Vampires");
        setFormElement(Locator.name("subjectColumnName"), "VampireId");
        checkRadioButton(Locator.radioButtonById("dateTimepointType"));
        clickButton("Create Study");
    }

    @Before
    public void preTest()
    {
        NUM_ERRORS = 0;
        goToProjectHome();
        clickFolder(getFolderName());
    }

    @Test
    public void createDomainErrorsTest()
    {
        log("Test for expected errors when creating a new domain with a missing property.");
        CreateDomainCommand createCmd = new CreateDomainCommand(DOMAIN_KIND, STUDY_DATASET_NAME);
        createCmd.setColumns(getColumnWithBlankNameProperty());
        testForExpectedErrorMessage(createCmd, BLANK_PROPERTY_ERROR_MSG);

        log("Test for expected errors when creating a new domain with duplicate columns.");
        createCmd = new CreateDomainCommand(DOMAIN_KIND, STUDY_DATASET_NAME);
        createCmd.setColumns(getColumnsWithDuplicateFields());
        testForExpectedErrorMessage(createCmd, DUPLICATE_FIELD_ERROR_MSG);

        int expectedErrorCount = 2;
        assertEquals("Create Domain error count mismatch.", expectedErrorCount, NUM_ERRORS);
    }

    @Test
    public void updateDomainErrorTest() throws IOException, CommandException
    {
        log("Create a study domain.");
        DomainResponse domainResponse = createDomainWithGoodColumns(getGoodColumns());
        long domainId = domainResponse.getDomainId();
        String domainURI = domainResponse.getDomainURI();

        log("Test for expected errors when saving an existing domain with a missing property.");
        SaveDomainCommand saveCmd = new SaveDomainCommand(DOMAIN_KIND, STUDY_DATASET_NAME);
        saveCmd.setDomainId((int)domainId);
        saveCmd.setDomainURI(domainURI);
        saveCmd.setSchemaName("study");
        saveCmd.setColumns(getColumnWithBlankNameProperty());
        testForExpectedErrorMessage(saveCmd, BLANK_PROPERTY_ERROR_MSG);

        log("Test for expected errors when saving an existing domain with duplicate columns.");
        saveCmd = new SaveDomainCommand(DOMAIN_KIND, STUDY_DATASET_NAME);
        saveCmd.setDomainId((int)domainId);
        saveCmd.setDomainURI(domainURI);
        saveCmd.setSchemaName("study");
        saveCmd.setColumns(getColumnsWithDuplicateFields());
        testForExpectedErrorMessage(saveCmd, DUPLICATE_FIELD_ERROR_MSG);

        int expectedErrorCount = 2;
        assertEquals("Update Domain error count mismatch.", expectedErrorCount, NUM_ERRORS);
    }

    private void testForExpectedErrorMessage(DomainCommand cmd, String expectedErrorMsg)
    {
        try
        {
            cmd.execute(this.createDefaultConnection(false), getCurrentContainerPath());
        }
        catch (IOException | CommandException e)
        {
            NUM_ERRORS++;
            assertEquals("Error message mismatch.", expectedErrorMsg, e.getMessage());
        }
    }

    public List<Map<String, Object>> getColumnWithBlankNameProperty()
    {
        List<Map<String, Object>> columns = new ArrayList<>();

        //missing name property
        Map<String, Object> col = new CaseInsensitiveHashMap<>();
        col.put("name", "");
        col.put("label", "Sun Exposure Time (secs)");
        col.put("rangeURI", "int");
        columns.add(0, col);

        return columns;
    }
    public List<Map<String, Object>> getColumnsWithDuplicateFields()
    {
        List<Map<String, Object>> columns = new ArrayList<>();
        Map<String, Object> col = new CaseInsensitiveHashMap<>();

        //duplicate columns
        col.put("name", "bloodLevel");
        col.put("label", "Blood Level (g/dL)");
        col.put("rangeURI", "double");
        columns.add(0, col);

        col = new CaseInsensitiveHashMap<>();
        col.put("name", "bloodLevel");
        col.put("label", "Blood Level (g/dL)");
        col.put("rangeURI", "double");
        columns.add(1, col);

        return columns;
    }

    public List<Map<String, Object>> getReservedColumn()
    {
        List<Map<String, Object>> columns = new ArrayList<>();
        Map<String, Object> col = new CaseInsensitiveHashMap<>();

        //reserved field
        col.put("name", "SequenceNum");
        col.put("label", "Sequence Num");
        col.put("rangeURI", "int");
        columns.add(0, col);

        return columns;
    }

    public List<Map<String, Object>> getGoodColumns()
    {
        List<Map<String, Object>> columns = new ArrayList<>();

        Map<String, Object> col = new CaseInsensitiveHashMap<>();
        col.put("name", "sunExposure");
        col.put("label", "Sun Exposure Time (secs)");
        col.put("rangeURI", "int");
        columns.add(0, col);

        col = new CaseInsensitiveHashMap<>();
        col.put("name", "bloodConsumed");
        col.put("label", "Blood Consumed (g/dL)");
        col.put("rangeURI", "double");
        columns.add(1, col);

        col = new CaseInsensitiveHashMap<>();
        col.put("name", "isPale");
        col.put("label", "Is Vampire Pale?");
        col.put("rangeURI", "boolean");
        columns.add(2, col);

        col = new CaseInsensitiveHashMap<>();
        col.put("name", "activityComments");
        col.put("label", "Unusual Activities");
        col.put("rangeURI", "String");
        columns.add(3, col);

        col = new CaseInsensitiveHashMap<>();
        col.put("name", "lastWeighed");
        col.put("label", "Last Weighed On");
        col.put("rangeURI", "date");
        columns.add(4, col);

        return columns;
    }

    private DomainResponse createDomainWithGoodColumns(List<Map<String, Object>> goodColumns) throws IOException, CommandException
    {
        CreateDomainCommand cmd = new CreateDomainCommand(DOMAIN_KIND, STUDY_DATASET_NAME);
        cmd.setColumns(goodColumns);
        return cmd.execute(this.createDefaultConnection(false), getCurrentContainerPath());
    }
}
