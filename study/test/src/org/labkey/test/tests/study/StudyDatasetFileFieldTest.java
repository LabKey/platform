package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyA;
import org.labkey.test.components.domain.DomainFormPanel;
import org.labkey.test.pages.DatasetInsertPage;
import org.labkey.test.pages.study.DatasetDesignerPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.DataRegionTable;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/*
Added the test to provide additional test coverage for below mentioned issue
https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=42309
 */

@Category({DailyA.class})
@BaseWebDriverTest.ClassTimeout(minutes = 10)
public class StudyDatasetFileFieldTest extends BaseWebDriverTest
{
    @BeforeClass
    public static void doSetup()
    {
        StudyDatasetFileFieldTest init = (StudyDatasetFileFieldTest) getCurrentTest();
        init.doCreateSteps();
    }

    @Nullable
    @Override
    protected String getProjectName()
    {
        return "Study Dataset File Field Project";
    }

    protected String getFolderName()
    {
        return "My Study";
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
        clickButton("Create Study");
    }

    @Test
    public void testFileField()
    {
        String datasetName = "Dataset-1";
        File filePath = TestFileUtils.getSampleData("fileTypes/xml_sample.xml"); //random file
        goToProjectHome();
        createDataset(datasetName);

        DatasetInsertPage insertDataPage = _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickViewData()
                .insertDatasetRow();

        insertDataPage.insert(Map.of("ParticipantId", "1",
                "SequenceNum", "2",
                "date", "2020-08-04",
                "fileField", filePath.toString(),
                "textField", "Hello World..!",
                "intField", "25"));

        log("Edit the dataset");
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        table.clickEditRow(0);
        setFormElement(Locator.name("quf_textField"), "Welcome..!");
        clickButton("Submit");

        log("Verify file field is not deleted");
        checker().verifyEquals("Incorrect file name ", " datasetdata\\xml_sample.xml", table.getDataAsText(0, "fileField"));
    }

    protected void createDataset(String name)
    {
        DatasetDesignerPage definitionPage = _studyHelper.goToManageDatasets()
                .clickCreateNewDataset()
                .setName(name);

        DomainFormPanel panel = definitionPage.getFieldsPanel();
        panel.manuallyDefineFields("textField");
        panel.addField(new FieldDefinition("intField").setType(FieldDefinition.ColumnType.Integer));
        panel.addField(new FieldDefinition("fileField").setType(FieldDefinition.ColumnType.File));
        definitionPage.clickSave();
    }
}
