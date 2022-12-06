/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.Daily;
import org.labkey.test.components.ext4.RadioButton;
import org.labkey.test.pages.study.DatasetDesignerPage;
import org.labkey.test.pages.study.ManageVisitPage;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.StudyHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.function.Function;

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 15)
public class StudyScheduleTest extends StudyBaseTest
{
    // dataset names
    private static final String IMPORT_DATASET = "ImportDataset";
    private static final String MANUAL_DATASET = "ManualDataset";
    private static final String GHOST_DATASET_1 = "GhostDataset1";
    private static final String GHOST_DATASET_2 = "GhostDataset2";
    private static final String GHOST_DATASET_3 = "GhostDataset3";
    private static final String GHOST_DATASET_4 = "GhostDataset4";
    private static final String GHOST_DATASET_5 = "GhostDataset5";
    private static final String GHOST_DATASET_6 = "GhostDataset6";

    // categories
    private static final String GHOST_CATEGORY = "GhostCategory";
    private static final String ASSAY_CATEGORY = "Assay Data";

    private static final String[][] datasets = {
            {"PRE-1: Pre-Existing Conditions", "Unlocked"},
            {"URS-1: Screening Urinalysis", "Draft"},
            {"LLS-1: Screening Local Lab Results (Page 1)", "Final"},
            {"LLS-2: Screening Local Lab Results (Page 2)", "Locked"}
    };

    private enum DatasetType {
        importFromFile,
        defineManually,
        placeholder,
        linkeToExisting,
    }

    private final String _folderName = getFolderName();

    private final PortalHelper portalHelper = new PortalHelper(this);

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override @LogMethod
    protected void doCreateSteps()
    {
        importStudy();
        startSpecimenImport(2);

        // wait for study and specimens to finish loading
        waitForSpecimenImport();
    }

    @Override
    protected void doVerifySteps()
    {
        basicTest(); //3min
        datasetStatusTest(); //2.5min
        linkDatasetTest();  //5.5min
        linkFromDatasetDetailsTest(); //4 min
        studyScheduleWebPartTest();
    }

    private void studyScheduleWebPartTest()
    {
        clickTab("Manage");
        click(Locator.linkWithText("Study Schedule"));
        shortWait().until(ExpectedConditions.elementToBeClickable(By.xpath("//div[.='ECI-1: Eligibility Criteria']/../../..//div[@class='unchecked']")));
        click(Locator.xpath("//div[.='ECI-1: Eligibility Criteria']/../../..//div[@class='unchecked']"));
        shortWait().until(ExpectedConditions.elementToBeClickable(By.xpath("//span[.='Save Changes']")));
        click(Locator.xpath("//span[.='Save Changes']"));
        waitForElement(Locator.xpath("//div[.='ECI-1: Eligibility Criteria']/../../..//div[@class='checked']"));
    }

    @LogMethod
    public void basicTest()
    {
        log("Study Schedule Test");
        String dataset = "PRE-1: Pre-Existing Conditions";
        String visit = "Screening Cycle";

        // check required timepoints
        goToStudySchedule();
        assertElementPresent(Locator.xpath("//div[@data-qtip='" + dataset + "']//..//..//..//td[6]//div[@class='checked']"));

        // change a required visit to optional
        portalHelper.clickWebpartMenuItem("Study Schedule", "Manage Visits");
        ManageVisitPage manageVisitPage = new ManageVisitPage(getDriver());
        manageVisitPage.goToEditVisit(visit);
        selectOption("datasetStatus", 2, "OPTIONAL");
        clickButton("Save");

        // verify that change is noted in schedule
        goToStudySchedule();
        assertElementPresent(Locator.xpath("//div[@data-qtip='" + dataset + "']//..//..//..//td[5]//div[@class='unchecked']"));

        // revert change
        portalHelper.clickWebpartMenuItem("Study Schedule", "Manage Visits");
        manageVisitPage = new ManageVisitPage(getDriver());
        manageVisitPage.goToEditVisit(visit);
        selectOption("datasetStatus", 2, "REQUIRED");
        clickButton("Save");

        // verify dataset 'data' link
        goToStudySchedule();
        click(Locator.xpath("//div[@data-qtip='" + dataset + "']//..//..//..//td[3]//a")); // go to dataset
        waitForElement(Locator.tagWithClass("table", "labkey-data-region"));
        waitForText(dataset);

        // test paging
        goToStudySchedule();
        waitForElement(Locator.css("a.button-next"), WAIT_FOR_JAVASCRIPT); //wait for next button to appear
        assertTextNotPresent("Cycle 2");
        click(Locator.css("a.button-next")); //click next button
        waitForText("Cycle 2");
        waitForText(dataset);
    }

    @LogMethod
    public void linkDatasetTest()
    {
        goToStudySchedule();

        // create a dataset from a file import
        addDataset(IMPORT_DATASET, ASSAY_CATEGORY, DatasetType.importFromFile);

        // verify it shows up in the schedule
        waitForElement(Locator.xpath("//div[text()='" + IMPORT_DATASET + "']"), WAIT_FOR_JAVASCRIPT);
        assertElementPresent(Locator.xpath("//div[text() = '" + IMPORT_DATASET + "']/../../../td//img[@alt='dataset']"));

        // click on the dataset link to verify it takes you to the dataset view
        click(Locator.xpath("//div[text() = '" + IMPORT_DATASET + "']/../../../td//a"));
        waitForElement(Locator.tagWithClass("table", "labkey-data-region"));
        assertTextPresent("Dataset:", IMPORT_DATASET);
        goToStudySchedule();

        // create a dataset from a manual definition
        addDataset(MANUAL_DATASET, null, DatasetType.defineManually);
        // verify it shows up in the schedule
        waitForElement(Locator.xpath("//div[text()='" + MANUAL_DATASET + "']"), WAIT_FOR_JAVASCRIPT);
        assertElementPresent(Locator.xpath("//div[text() = '" + MANUAL_DATASET + "']/../../../td//img[@alt='dataset']"));

        // create and verify a placeholder datasets
        createPlaceholderDataset(GHOST_DATASET_1, GHOST_CATEGORY, true);
        createPlaceholderDataset(GHOST_DATASET_2, null, false);
        createPlaceholderDataset(GHOST_DATASET_3, GHOST_CATEGORY, false);

        // link the placeholder datasets
        linkDatasetFromSchedule(GHOST_DATASET_1, DatasetType.linkeToExisting, IMPORT_DATASET);

        // verify the expectation dataset gets converted and the existing dataset is gone
        waitForElement(Locator.xpath("//div[text()='" + GHOST_DATASET_1 + "']"), WAIT_FOR_JAVASCRIPT);
        assertElementPresent(Locator.xpath("//div[text() = '" + GHOST_DATASET_1 + "']/../../../td//img[@alt='dataset']"));
        assertElementNotPresent(Locator.xpath("//div[text() = '" + IMPORT_DATASET + "']"));

        // click on the dataset link to verify it takes you to the dataset view
        click(Locator.xpath("//div[text() = '" + GHOST_DATASET_1 + "']/../../../td//a"));
        waitForElement(Locator.tagWithClass("table", "labkey-data-region"));
        assertTextPresent("Dataset:", GHOST_DATASET_1);
        goToStudySchedule();

        // link manually
        linkDatasetFromSchedule(GHOST_DATASET_2, DatasetType.defineManually, null);

        // link by importing file
        linkDatasetFromSchedule(GHOST_DATASET_3, DatasetType.importFromFile, null);

        // verify the expectation dataset gets converted and the existing dataset is gone
        waitForElement(Locator.xpath("//div[text()='" + GHOST_DATASET_3 + "']"), WAIT_FOR_JAVASCRIPT);
        assertElementPresent(Locator.xpath("//div[text() = '" + GHOST_DATASET_3 + "']/../../../td//img[@alt='dataset']"));

        // click on the dataset link to verify it takes you to the dataset view
        click(Locator.xpath("//div[text() = '" + GHOST_DATASET_3 + "']/../../../td//a"));
        waitForElement(Locator.tagWithClass("table", "labkey-data-region"));
        assertTextPresent("Dataset:", GHOST_DATASET_3);
    }

    @LogMethod
    public void linkFromDatasetDetailsTest()
    {
        goToStudySchedule();

        // create and verify a placeholder datasets
        createPlaceholderDataset(GHOST_DATASET_4, GHOST_CATEGORY, true);
        createPlaceholderDataset(GHOST_DATASET_5, null, false);
        createPlaceholderDataset(GHOST_DATASET_6, GHOST_CATEGORY, false);

        goToManageStudy();
        clickAndWait(Locator.linkWithText("Manage Datasets"));

        linkDatasetFromDetails(GHOST_DATASET_4, DatasetType.linkeToExisting, "CPS-1: Screening Chemistry Panel");
        waitForElement(Locator.lkButton("Edit Definition"));
        _studyHelper.goToManageDatasets();
        assertElementPresent(Locator.linkWithText(GHOST_DATASET_4));
        assertElementNotPresent(Locator.linkWithText("CPS-1: Screening Chemistry Panel"));
        linkDatasetFromDetails(GHOST_DATASET_5, DatasetType.defineManually, null);
        _studyHelper.goToManageDatasets();
        linkDatasetFromDetails(GHOST_DATASET_6, DatasetType.importFromFile, null);
        _studyHelper.goToManageDatasets();
    }

    @LogMethod
    private void createPlaceholderDataset(String name, String category, boolean verify)
    {
        // create and verify a placeholder dataset
        addDataset(name, category, DatasetType.placeholder);
        waitForElement(Locator.xpath("//div[text()='" + name + "']"), WAIT_FOR_JAVASCRIPT);
        assertElementPresent(Locator.xpath("//div[text() = '" + name + "']/../../../td//img[@alt='link data']"));

        if (verify)
        {
            // verify a placeholder dataset cannot be edited from the manage dataset page
            clickFolder(_folderName);
            _studyHelper.goToManageDatasets()
                    .selectDatasetByName(name);

            assertTextNotPresent("View Data", "Edit Definition");
            goToStudySchedule();
        }
    }

    @LogMethod
    private void addDataset(String name, String category, DatasetType type)
    {
        log("adding dataset: " + name + " type: " + type);

        clickButton("Add Dataset", 0);
        WebElement dialogHeader = waitForElement(Locator.xpath("//span[text() = 'New Dataset']"), WAIT_FOR_JAVASCRIPT);
        setFormElement(Locator.xpath("//label[text() = 'Name:']/../..//input"), name);

        if (category != null)
        {
            setFormElement(Locator.xpath("//label[text() = 'Category:']/../..//input"), category);
        }

        DatasetDesignerPage datasetDesignerPage;

        switch (type)
        {
            case defineManually:
                click(Ext4Helper.Locators.ext4Radio("Define dataset manually or from a file"));
                clickButton("Next");

                // add a single name field
                datasetDesignerPage = new DatasetDesignerPage(getDriver());
                datasetDesignerPage.getFieldsPanel().manuallyDefineFields("antigenName");
                datasetDesignerPage.clickSave();

                break;
            case importFromFile:
                click(Ext4Helper.Locators.ext4Radio("Define dataset manually or from a file"));
                clickButton("Next");

                File file = StudyHelper.getFolderArchiveFile("study/datasets/plate002.tsv");
                datasetDesignerPage = new DatasetDesignerPage(getDriver());
                datasetDesignerPage.getFieldsPanel()
                        .setInferFieldFile(file);
                datasetDesignerPage.setAutoImport(true)
                        .setPreviewMappedColumn("Mice", "ptid")
                        .setPreviewMappedColumn("Visits", "visit")
                        .clickSave();

                break;
            case placeholder:
                click(Ext4Helper.Locators.ext4Radio("do this later"));
                clickButton("Done", 0);
                shortWait().until(ExpectedConditions.invisibilityOf(dialogHeader));

                break;
        }
        goToStudySchedule();
    }

    @LogMethod
    private void linkDatasetFromSchedule(String name, DatasetType type, String targetDataset)
    {
        log("linking dataset: " + name + " to type: " + type + " from study schedule.");
        waitForElement(Locator.xpath("//div[text()='" + name + "']"), WAIT_FOR_JAVASCRIPT);

        Locator link = Locator.xpath("//div[text() = '" + name + "']/../../../td//img[@alt='link data']/../..//div");
        assertElementPresent(link);

        click(link);
        log("show define dataset dialog");
        _extHelper.waitForExtDialog("Define Dataset");

        linkDataset(name, type, targetDataset);
        goToStudySchedule();
    }

    @LogMethod
    private void linkDatasetFromDetails(String name, DatasetType type, String targetDataset)
    {
        log("linking dataset: " + name + " to type: " + type + "from dataset details.");

        clickAndWait(Locator.linkContainingText(name));
        WebElement linkButton = Locator.xpath("//span[text()='Link or Define Dataset']").waitForElement(shortWait());
        // Workaround for unresponsive dataset details buttons
        shortWait().until(new Function<WebDriver, WebElement>()
        {
            @Nullable
            @Override
            public WebElement apply(@Nullable WebDriver input)
            {
                try
                {
                    linkButton.click();
                }
                catch (WebDriverException ignore) {}

                return Locator.xpath("//div[contains(@class, 'x4-form-display-field')][text()='Define " + name + "']")
                        .findElementOrNull(getDriver());
            }
        });

        linkDataset(name, type, targetDataset);
    }

    @LogMethod
    private void linkDataset(@LoggedParam String name, DatasetType type, String targetDataset)
    {
        DatasetDesignerPage datasetDesignerPage;

        switch (type)
        {
            case defineManually:
                click(Ext4Helper.Locators.ext4Radio("Define dataset manually or from a file"));
                clickButton("Next");

                // add a single name field
                datasetDesignerPage = new DatasetDesignerPage(getDriver());
                datasetDesignerPage.getFieldsPanel().manuallyDefineFields("antigenName");
                datasetDesignerPage.clickSave();

                break;
            case importFromFile:
                click(Ext4Helper.Locators.ext4Radio("Define dataset manually or from a file"));
                clickButton("Next");

                File file = StudyHelper.getFolderArchiveFile("study/datasets/plate002.tsv");
                datasetDesignerPage = new DatasetDesignerPage(getDriver());
                datasetDesignerPage.getFieldsPanel()
                        .setInferFieldFile(file);
                datasetDesignerPage.setAutoImport(true)
                        .setPreviewMappedColumn("Mice", "ptid")
                        .setPreviewMappedColumn("Visits", "visit")
                        .clickSave();

                break;
            case linkeToExisting:
                RadioButton link_to_existing_dataset = new RadioButton.RadioButtonFinder().withLabel("Link to existing dataset").find(getDriver());
                link_to_existing_dataset.check();

                Locator.XPathLocator comboParent = Locator.xpath("//table[contains(@class, 'existing-dataset-combo')]");
                _ext4Helper.selectComboBoxItem(comboParent, targetDataset);

                clickButton("Done", 0);
                shortWait().until(ExpectedConditions.invisibilityOfAllElements(Collections.singletonList(link_to_existing_dataset.getComponentElement())));
                break;
        }
    }

    @LogMethod
    public void datasetStatusTest()
    {
        log("Testing status settings for datasets");

        goToStudySchedule();

        for (String[] entry : datasets)
        {
            clickCustomizeView(entry[0]);

            _ext4Helper.selectComboBoxItem("Status", entry[1]);

            clickButton("Save", 0);

            Locator statusLink = Locator.xpath("//div[contains(@class, 'x4-grid-cell-inner')]//div[contains(text(), '" + entry[0] + "')]/../../..//img[@alt='" + entry[1] + "']");
            waitForElement(statusLink, WAIT_FOR_JAVASCRIPT);

            // visit the dataset page and make sure we inject the correct class onto the page
            log("Verify dataset view has the watermark class");
            Locator datasetLink = Locator.xpath("//div[contains(@class, 'x4-grid-cell-inner')]//div[contains(text(), '" + entry[0] + "')]/../../..//a");
            click(datasetLink);
           // waitForElement(Locator.xpath("//table[contains(@class, 'labkey-proj') and contains(@class, 'labkey-dataset-status-" + entry[1].toLowerCase() + "')]"), WAIT_FOR_JAVASCRIPT);

            goToStudySchedule();
        }
    }

    @LogMethod
    private void goToStudySchedule()
    {
        clickFolder(_folderName);
        goToManageStudy();
        clickAndWait(Locator.linkWithText("Study Schedule"));

        // wait for grid to load
        waitForText("verifyAssay"); // verify dataset column
        waitForText("Termination"); // verify timepoint
    }

    private void clickCustomizeView(String viewName)
    {
        Locator editLink = Locator.xpath("//div[contains(@class, 'x4-grid-cell-inner')]//div[contains(text(), '" + viewName + "')]/../../..//span[contains(@class, 'edit-views-link')]");
        waitForElement(editLink, WAIT_FOR_JAVASCRIPT);
        click(editLink);

        _extHelper.waitForExtDialog(viewName);
    }
}
