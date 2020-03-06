package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.DailyA;
import org.labkey.test.components.PropertiesEditor;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PortalHelper;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

@Category({DailyA.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class StudyDateAndContinuousTimepointTest extends BaseWebDriverTest
{
    private String datasetName = "SampleDataset";

    @BeforeClass
    public static void doSetup()
    {
        StudyDateAndContinuousTimepointTest init = (StudyDateAndContinuousTimepointTest) getCurrentTest();
        init.doCreateSteps();
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Nullable
    @Override
    protected String getProjectName()
    {
        return "StudyDateAndContinuousTimepointTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    private void doCreateSteps()
    {
        _containerHelper.createProject(getProjectName(), "Study");
        clickButton("Create Study");
        click(Locator.radioButtonById("continuousTimepointType"));
        clickButton("Create Study");

        goToProjectHome();
        PortalHelper portalHelper = new PortalHelper(this);
        portalHelper.addWebPart("Datasets");

    }

    @Test
    public void testSteps()
    {
        goToProjectHome();
        changeTimepointType("date");

        EditDatasetDefinitionPage editDatasetPage = _studyHelper
                .goToManageDatasets()
                .clickCreateNewDataset()
                .setName(datasetName)
                .submit();

        PropertiesEditor fieldsEditor = editDatasetPage.getFieldsEditor();
        fieldsEditor.selectField(0).markForDeletion();
        fieldsEditor.addField(new FieldDefinition("TestDate").setLabel("TestDate").setType(FieldDefinition.ColumnType.DateTime));
        editDatasetPage.save();

        goToProjectHome();
        clickAndWait(Locator.linkWithText(datasetName));
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        table.clickInsertNewRow();

        setFormElement(Locator.name("quf_ParticipantId"), "P1");
        setFormElement(Locator.name("quf_date"), getDate());
        setFormElement(Locator.name("quf_TestDate"), getDate());
        clickAndWait(Locator.tagWithText("span", "Submit"));

        goToProjectHome();
        changeTimepointType("continuous");
        clickAndWait(Locator.linkWithText(datasetName));
        table = new DataRegionTable("Dataset", getDriver());
        checker().verifyEquals("Date field changed when study is changed to continuous", 1,
                table.getColumnDataAsText("TestDate"));
    }

    private void changeTimepointType(String type)
    {
        clickTab("Manage");
        waitAndClick(Locator.linkContainingText("Change Study Properties"));
        waitForElement(Locator.id(type));
        checkRadioButton(Locator.id(type));
        clickAndWait(Ext4Helper.Locators.ext4Button("Submit"));
        goToProjectHome();
    }

    private String getDate()
    {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        return format.format(Calendar.getInstance().getTime());
    }

}
