package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyA;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.components.SetDefaultPageClass;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PortalHelper;

import java.util.Arrays;
import java.util.List;

@Category({DailyA.class})
@BaseWebDriverTest.ClassTimeout(minutes = 2)
public class StudyDatasetDefaultView extends BaseWebDriverTest
{
    @BeforeClass
    public static void doSetup()
    {
        StudyDatasetDefaultView init = (StudyDatasetDefaultView) getCurrentTest();
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
        return "StudyDatasetDefaultView Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    private void doCreateSteps()
    {
        _containerHelper.createProject(getProjectName(), "Study");
        importFolderFromZip(TestFileUtils.getSampleData("studies/LabkeyDemoStudy.zip"));

        goToProjectHome();
        PortalHelper portalHelper = new PortalHelper(this);
        portalHelper.addWebPart("Datasets");
    }

    //Test coverage added for https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=38340
    @Test
    public void testSetDefaultGridView()
    {
        goToProjectHome();
        String datasetName = "GenericAssay";
        String gridName = "GRID1";

        log("Creating the custom grid view");
        clickAndWait(Locator.linkWithText(datasetName));
        DataRegionTable datasetTable = new DataRegionTable("Dataset", getDriver());
        CustomizeView datasetTableCustomizer = datasetTable.getCustomizeView();
        datasetTableCustomizer.openCustomizeViewPanel();
        waitForText("Available Fields");
        datasetTableCustomizer.removeColumn("M1");
        datasetTableCustomizer.removeColumn("M2");
        datasetTableCustomizer.removeColumn("M3");
        datasetTableCustomizer.addColumn("Day");
        datasetTableCustomizer.saveCustomView(gridName, true);

        log("Setting the default view");
        SetDefaultPageClass setDefaultPage = datasetTable.clicksetDefault();
        setDefaultPage.selectDefaultGrid(gridName);

        log("Verifying default view for the dataset.");
        goToProjectHome();
        clickAndWait(Locator.linkWithText(datasetName));
        checker().verifyTrue("Not the correct default view", isElementPresent(Locator.tagWithText("span", gridName)));
    }

    //Test coverage added for https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=40502
    @Test
    public void testSetDefaultWithDatasetDesc()
    {
        goToProjectHome();
        String datasetName = "MicroarrayAssay";

        log("Creating the default custom view");
        clickAndWait(Locator.linkWithText(datasetName));
        DataRegionTable datasetTable = new DataRegionTable("Dataset", getDriver());
        CustomizeView datasetTableCustomizer = datasetTable.getCustomizeView();
        datasetTableCustomizer.openCustomizeViewPanel();
        waitForText("Available Fields");
        datasetTableCustomizer.addColumn("Day");
        datasetTableCustomizer.saveDefaultView();

        log("Verifying default view for the dataset.");
        goToProjectHome();
        clickAndWait(Locator.linkWithText(datasetName));
        datasetTable = new DataRegionTable("Dataset",getDriver());
        checker().verifyEquals("Not the correct default view", Arrays.asList("126","126"),datasetTable.getColumnDataAsText("Day"));
    }
}
