package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.Command;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.domain.CreateDomainCommand;
import org.labkey.remoteapi.domain.Domain;
import org.labkey.remoteapi.domain.DomainResponse;
import org.labkey.remoteapi.domain.GetDomainCommand;
import org.labkey.remoteapi.domain.PropertyDescriptor;
import org.labkey.remoteapi.domain.SaveDomainCommand;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.study.CreateStudyPage;
import org.labkey.test.util.StudyHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class StudyDatasetDomainTest extends BaseWebDriverTest
{
    private final String DOMAIN_KIND = "StudyDatasetDate";
    private final String STUDY_SCHEMA = "Study";//schemaName
    private final String STUDY_DATASET_NAME = "vampireBloodLevels";

    private final String BLANK_PROPERTY_ERROR_MSG = "Please provide a name for each field.";
    private final String DUPLICATE_FIELD_ERROR_MSG = "The field name 'bloodLevel' is already taken. Please provide a unique name for each field.";
    private final String TYPE_CHANGE_ERROR_MSG = "Cannot convert an instance of VARCHAR to INTEGER.";

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

        CreateStudyPage createStudyPage = new StudyHelper(this).startCreateStudy();
        createStudyPage.setSubjectNounSingular("Vampire");
        createStudyPage.setSubjectNounPlural("Vampires");
        createStudyPage.setSubjectColumnName("VampireId");
        createStudyPage.setTimepointType(StudyHelper.TimepointType.DATE);
        createStudyPage.createStudy();
    }

    private String getContainerPath()
    {
        return getProjectName() + "/" + getFolderName();
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
        clickFolder(getFolderName());
    }

    @Test
    public void createDomainErrorsTest() throws IOException
    {
        log("Test for an expected error when creating a new domain with a missing property.");
        CreateDomainCommand createCmd = new CreateDomainCommand(DOMAIN_KIND, STUDY_DATASET_NAME);
        createCmd.getDomainDesign().setFields(getColumnWithBlankNameProperty());
        testForExpectedErrorMessage(createCmd, STUDY_DATASET_NAME + " -- " + BLANK_PROPERTY_ERROR_MSG, "CreateDomain");

        log("Test for an expected error when creating a new domain with duplicate columns.");
        createCmd = new CreateDomainCommand(DOMAIN_KIND, STUDY_DATASET_NAME);
        createCmd.getDomainDesign().setFields(getColumnsWithDuplicateFields());
        testForExpectedErrorMessage(createCmd, STUDY_DATASET_NAME + " -- " + DUPLICATE_FIELD_ERROR_MSG, "CreateDomain");
    }

    @Test
    public void updateDomainErrorsTest() throws Exception
    {
        log("Create a study domain.");
        DomainResponse domainResponse = createDomainWithGoodColumns(getGoodColumns());
        Domain existingDomain = domainResponse.getDomain();

        log("Test for an expected error when saving an existing domain with a missing property.");
        SaveDomainCommand saveCmd = new SaveDomainCommand(STUDY_SCHEMA, STUDY_DATASET_NAME);
        Domain updatedDomain = saveCmd.getDomainDesign();
        updatedDomain.setDomainId(existingDomain.getDomainId());
        updatedDomain.setDomainURI(existingDomain.getDomainURI());
        updatedDomain.setFields(getColumnWithBlankNameProperty());
        testForExpectedErrorMessage(saveCmd, BLANK_PROPERTY_ERROR_MSG, "SaveDomain");

        log("Test for an expected error when saving an existing domain with duplicate columns.");
        saveCmd = new SaveDomainCommand(STUDY_SCHEMA, STUDY_DATASET_NAME);
        updatedDomain = saveCmd.getDomainDesign();
        updatedDomain.setDomainId(existingDomain.getDomainId());
        updatedDomain.setDomainURI(existingDomain.getDomainURI());
        updatedDomain.setFields(getColumnsWithDuplicateFields());
        testForExpectedErrorMessage(saveCmd, DUPLICATE_FIELD_ERROR_MSG, "SaveDomain");

        log("Rename 'activityComments' column to 'activityCode'.");
        renameColumnName();

        log("Test for an expected error when changing type from String to Int");
        GetDomainCommand getCmd = new GetDomainCommand(STUDY_SCHEMA, STUDY_DATASET_NAME);
        DomainResponse getDomainResponse = getCmd.execute(this.createDefaultConnection(), getContainerPath());
        List<PropertyDescriptor> getDomainCols = getDomainResponse.getDomain().getFields();
        PropertyDescriptor activityCodeCol = getDomainCols.get(3);

        if ("activityCode".equalsIgnoreCase(activityCodeCol.getName()))
        {
            activityCodeCol.setRangeURI("int");

            saveCmd = new SaveDomainCommand(STUDY_SCHEMA, STUDY_DATASET_NAME);
            existingDomain = getDomainResponse.getDomain();
            updatedDomain = saveCmd.getDomainDesign();

            updatedDomain.setDomainId(existingDomain.getDomainId());
            updatedDomain.setDomainURI(existingDomain.getDomainURI());
            updatedDomain.setFields(getDomainCols);

            testForExpectedErrorMessage(saveCmd, TYPE_CHANGE_ERROR_MSG, "SaveDomain");
        }
        else
        {
            throw new Exception("Renamed column 'activityCode' not found.");
        }

    }

    private void renameColumnName() throws IOException, CommandException
    {
        GetDomainCommand getCmd = new GetDomainCommand(STUDY_SCHEMA, STUDY_DATASET_NAME);
        DomainResponse getDomainResponse = getCmd.execute(this.createDefaultConnection(), getContainerPath());
        List<PropertyDescriptor> getDomainCols = getDomainResponse.getDomain().getFields();
        PropertyDescriptor activityCommentsCol = getDomainCols.get(3);

        activityCommentsCol.setName("activityCode"); //rename from activityComments

        SaveDomainCommand saveCmd = new SaveDomainCommand(STUDY_SCHEMA, STUDY_DATASET_NAME);
        Domain existingDomain = getDomainResponse.getDomain();
        Domain updatedDomain = saveCmd.getDomainDesign();

        updatedDomain.setDomainId(existingDomain.getDomainId());
        updatedDomain.setDomainURI(existingDomain.getDomainURI());
        updatedDomain.setFields(getDomainCols);
        saveCmd.execute(this.createDefaultConnection(), getContainerPath());
    }

    private void testForExpectedErrorMessage(Command<?> cmd, String expectedErrorMsg, String domainApiType) throws IOException
    {
        try
        {
            cmd.execute(this.createDefaultConnection(), getContainerPath());
            fail("Expected " + domainApiType + " API to throw CommandException.");
        }
        catch (CommandException e)
        {
            assertEquals("Error message mismatch.", expectedErrorMsg, e.getMessage());
        }
    }

    public List<PropertyDescriptor> getColumnWithBlankNameProperty()
    {
        //missing name property
        return Collections.singletonList(new PropertyDescriptor("", "Sun Exposure Time (secs)", "int"));
    }

    public List<PropertyDescriptor> getColumnsWithDuplicateFields()
    {
        List<PropertyDescriptor> columns = new ArrayList<>();

        //duplicate columns
        columns.add(new PropertyDescriptor("bloodLevel", "Blood Level (g/dL)", "double"));
        columns.add(new PropertyDescriptor("bloodLevel", "Blood Level (g/dL)", "double"));

        return columns;
    }

    public List<PropertyDescriptor> getReservedColumn()
    {
        //reserved field
        return Collections.singletonList(new PropertyDescriptor("SequenceNum", "Sequence Num", "int"));
    }

    public List<PropertyDescriptor> getGoodColumns()
    {
        List<PropertyDescriptor> columns = new ArrayList<>();

        columns.add(new PropertyDescriptor("sunExposure", "Sun Exposure Time (secs)", "int"));
        columns.add(new PropertyDescriptor("bloodConsumed", "Blood Consumed (g/dL)", "double"));
        columns.add(new PropertyDescriptor("isPale", "Is Vampire Pale?", "boolean"));
        columns.add(new PropertyDescriptor("activityComments", "Unusual Activities", "String"));
        columns.add(new PropertyDescriptor("lastWeighed", "Last Weighed On", "date"));

        return columns;
    }

    private DomainResponse createDomainWithGoodColumns(List<PropertyDescriptor> goodColumns) throws IOException, CommandException
    {
        CreateDomainCommand cmd = new CreateDomainCommand(DOMAIN_KIND, STUDY_DATASET_NAME);
        cmd.getDomainDesign().setFields(goodColumns);
        return cmd.execute(this.createDefaultConnection(), getContainerPath());
    }
}
