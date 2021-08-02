package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.Daily;
import org.labkey.test.components.domain.DomainFormPanel;
import org.labkey.test.components.dumbster.EmailRecordTable;
import org.labkey.test.pages.study.DatasetDesignerPage;
import org.labkey.test.pages.study.ManageStudyNotificationPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PortalHelper;

import java.util.Arrays;
import java.util.List;

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 3)
public class StudyDatasetChangeEmailNotificationTest extends BaseWebDriverTest
{
    static public int emailCount = 1; //Initialised 1 because the initial email is sent while dataset creation in setUp.

    @BeforeClass
    public static void doSetup()
    {
        StudyDatasetChangeEmailNotificationTest init = (StudyDatasetChangeEmailNotificationTest) getCurrentTest();
        init.doCreateSteps();
    }

    @Override
    protected @Nullable String getProjectName()
    {
        return "Study Dataset Change Email Notification Test Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return null;
    }

    private void doCreateSteps()
    {
        _containerHelper.createProject(getProjectName(), "Study");
        _studyHelper.startCreateStudy().createStudy();

        goToProjectHome();
        new PortalHelper(getDriver()).addWebPart("Datasets");

        log("Creating sample datasets");
        createDataset("D1", Arrays.asList("F1", "F2"), "C1");
        createDataset("D2", Arrays.asList("F2", "F3"), "C2");
        createDataset("D3", Arrays.asList("F21", "F12"), "C1");
        createDataset("D4", Arrays.asList("F33", "F31"), "C2");
        createDataset("D5", Arrays.asList("F11", "F24"), null);

    }

    @Test
    public void testEmailNotificationForAll()
    {
        String datasetName = "D1";
        goToProjectHome();
        goToManageViews();
        clickAndWait(Locator.linkWithText("Manage Notifications"));
        ManageStudyNotificationPage notificationPage = new ManageStudyNotificationPage(getDriver());
        notificationPage.selectAll().save();

        goToProjectHome();
        clickAndWait(Locator.linkWithText(datasetName));
        new DataRegionTable("Dataset", getDriver()).clickInsertNewRow();
        setFormElement(Locator.name("quf_ParticipantId"), "P1");
        setFormElement(Locator.name("quf_SequenceNum"), "1");
        clickButton("Submit");

        log("Execute the script to send the email");
        executeScript("LABKEY.Ajax.request({ url: '/labkey/home/reports-sendDailyDigest.view', method: 'POST' });");

        goToModule("Dumbster");
        EmailRecordTable notifications = new EmailRecordTable(this);
        while (notifications.getEmailCount() < emailCount)
            refresh();

        log("Verifying the email for sent for dataset change");
        notifications.clickSubjectAtIndex("Report/Dataset Change Notification", 0);
        clickAndWait(Locator.linkWithText("view details"));
        DataRegionTable table = new DataRegionTable("query", getDriver());
        checker().verifyEquals("Incorrect number of changes recorded", 1, table.getDataRowCount());
        checker().verifyEquals("Incorrect comment for inserted row", "A new dataset record was inserted",
                table.getDataAsText(0, "comment"));
    }

    @Test
    public void testEmailNotificationByCategory()
    {
        String datasetName = "D3";
        String categoryName = "C1";
        goToProjectHome();
        goToManageViews();
        clickAndWait(Locator.linkWithText("Manage Notifications"));
        ManageStudyNotificationPage notificationPage = new ManageStudyNotificationPage(getDriver());
        notificationPage.selectByCategory(categoryName).save();

        log("Inserting in same category dataset set in notification page");
        goToProjectHome();
        clickAndWait(Locator.linkWithText(datasetName));
        new DataRegionTable("Dataset", getDriver()).clickInsertNewRow();
        setFormElement(Locator.name("quf_ParticipantId"), "P2");
        setFormElement(Locator.name("quf_SequenceNum"), "2");
        clickButton("Submit");

        log("Inserting in different category dataset");
        goToProjectHome();
        clickAndWait(Locator.linkWithText("D2"));
        new DataRegionTable("Dataset", getDriver()).clickInsertNewRow();
        setFormElement(Locator.name("quf_ParticipantId"), "P3");
        setFormElement(Locator.name("quf_SequenceNum"), "3");
        clickButton("Submit");

        log("Execute the script to send the email");
        executeScript("LABKEY.Ajax.request({ url: '/labkey/home/reports-sendDailyDigest.view', method: 'POST' });");

        goToModule("Dumbster");
        EmailRecordTable notifications = new EmailRecordTable(this);
        while (notifications.getEmailCount() < emailCount)
            refresh();

        log("Verifying the email for sent for dataset change");
        notifications.clickSubjectAtIndex("Report/Dataset Change Notification", 0);
        clickAndWait(Locator.linkWithText("view details"));
        DataRegionTable table = new DataRegionTable("query", getDriver());
        checker().verifyEquals("Incorrect number of changes recorded", 1, table.getDataRowCount());
        checker().verifyEquals("Incorrect dataset", datasetName, table.getDataAsText(0, "datasetid"));
        checker().verifyEquals("Incorrect comment for inserted row", "A new dataset record was inserted",
                table.getDataAsText(0, "comment"));
    }

    @Test
    public void testEmailNotificationByDataset()
    {
        String datasetName = "D5";
        goToProjectHome();
        goToManageViews();
        clickAndWait(Locator.linkWithText("Manage Notifications"));
        ManageStudyNotificationPage notificationPage = new ManageStudyNotificationPage(getDriver());
        notificationPage.selectByDataset(datasetName).save();

        log("Inserting in same dataset in notification page");
        goToProjectHome();
        clickAndWait(Locator.linkWithText(datasetName));
        new DataRegionTable("Dataset", getDriver()).clickInsertNewRow();
        setFormElement(Locator.name("quf_ParticipantId"), "P4");
        setFormElement(Locator.name("quf_SequenceNum"), "4");
        clickButton("Submit");

        log("Inserting in different dataset");
        goToProjectHome();
        clickAndWait(Locator.linkWithText("D2"));
        new DataRegionTable("Dataset", getDriver()).clickInsertNewRow();
        setFormElement(Locator.name("quf_ParticipantId"), "P5");
        setFormElement(Locator.name("quf_SequenceNum"), "5");
        clickButton("Submit");

        log("Execute the script to send the email");
        executeScript("LABKEY.Ajax.request({ url: '/labkey/home/reports-sendDailyDigest.view', method: 'POST' });");

        goToModule("Dumbster");
        EmailRecordTable notifications = new EmailRecordTable(this);
        while (notifications.getEmailCount() < emailCount)
            refresh();

        notifications.clickSubjectAtIndex("Report/Dataset Change Notification", 0);
        clickAndWait(Locator.linkWithText("view details"));
        DataRegionTable table = new DataRegionTable("query", getDriver());
        checker().verifyEquals("Incorrect number of changes recorded", 1, table.getDataRowCount());
        checker().verifyEquals("Incorrect dataset", datasetName, table.getDataAsText(0, "datasetid"));
        checker().verifyEquals("Incorrect comment for inserted row", "A new dataset record was inserted",
                table.getDataAsText(0, "comment"));
    }

    private void createDataset(String name, List<String> fields, @Nullable String category)
    {
        goToProjectHome();
        DatasetDesignerPage definitionPage = _studyHelper.goToManageDatasets()
                .clickCreateNewDataset()
                .setName(name);
        if (category != null)
            definitionPage.setCategory(category);

        DomainFormPanel panel = definitionPage.getFieldsPanel();
        for (String field : fields)
            panel.addField(field);
        definitionPage.clickSave();
    }


}
