package org.labkey.test.tests.query;

import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locators;
import org.labkey.test.categories.DailyC;
import org.labkey.test.util.APIContainerHelper;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PermissionsHelper;
import org.openqa.selenium.WebElement;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@Category({DailyC.class})
public class ContainerFilterQueryTest extends BaseWebDriverTest
{
    private static final String OTHER_PROJECT = "Other ContainerFilterQueryTest Project"; // for permissions check
    private static final String FOLDER_NAME = "CF Subfolder";
    private static final String USER = "cfq_user@containerfilterquery.test";

    public ContainerFilterQueryTest()
    {
        ((APIContainerHelper) _containerHelper).setNavigateToCreatedFolders(false);
    }

    @Override
    protected void doCleanup(boolean afterTest)
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
        _containerHelper.deleteProject(OTHER_PROJECT, false);
        _userHelper.deleteUsers(false, USER);
    }

    @BeforeClass
    public static void setupProject()
    {
        ContainerFilterQueryTest init = (ContainerFilterQueryTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), null);
        _containerHelper.createSubfolder(getProjectName(), FOLDER_NAME);
    }

    @Test
    public void testInvalidContainerFilterAnnotation()
    {
        final String sql = "SELECT Containers.Name FROM Containers";
        final String invalidSql = sql + "[ContainerFilter='Invalid']";
        String queryName = "testInvalidContainerFilterAnnotation";

        createQuery(getProjectName(), queryName, "core", sql, null, false);
        setCodeEditorValue("queryText", invalidSql);
        clickButton("Save", 0);

        WebElement error = waitForElement(Locators.labkeyError);
        assertThat("Wrong error message.", error.getText(),
            containsString("Unrecognized container filter type"));
    }

    @Test
    public void testCurrentFolderAnnotation()
    {
        final String sql = "SELECT Containers.Name FROM Containers[ContainerFilter='Current']";
        String queryName = "testCurrentFolderAnnotation";

        DataRegionTable table = createQuery(getProjectName(), queryName, "core", sql);
        List<String> containerNames = table.getColumnDataAsText("Name");
        assertEquals("Wrong containers for 'Current' container filter.",
            List.of(getProjectName()), containerNames);

        table.setContainerFilter(DataRegionTable.ContainerFilterType.ALL_FOLDERS);
        containerNames = table.getColumnDataAsText("Name");
        assertEquals("ContainerFilter annotation should ignore data region container filter.",
            List.of(getProjectName()), containerNames);

        table.setContainerFilter(DataRegionTable.ContainerFilterType.CURRENT_AND_SUBFOLDERS);
        containerNames = table.getColumnDataAsText("Name");
        assertEquals("ContainerFilter annotation should ignore data region container filter.",
            List.of(getProjectName()), containerNames);
    }

    @Test
    public void testCurrentAndSubfoldersAnnotation()
    {
        final String sql = "SELECT Containers.Name FROM Containers[ContainerFilter='CurrentAndSubfolders']";
        String queryName = "testCurrentAndSubfoldersAnnotation";
        List<String> expectedFolders = List.of(getProjectName(), FOLDER_NAME);

        DataRegionTable table = createQuery(getProjectName(), queryName, "core", sql);
        List<String> containerNames = table.getColumnDataAsText("Name");
        assertEquals("Wrong containers for 'CurrentAndSubfolders' container filter.",
            expectedFolders, containerNames);

        table.setContainerFilter(DataRegionTable.ContainerFilterType.CURRENT_FOLDER);
        containerNames = table.getColumnDataAsText("Name");
        assertEquals("ContainerFilter annotation should ignore data region container filter.", expectedFolders, containerNames);

        table.setContainerFilter(DataRegionTable.ContainerFilterType.ALL_FOLDERS);
        containerNames = table.getColumnDataAsText("Name");
        assertEquals("ContainerFilter annotation should ignore data region container filter.", expectedFolders, containerNames);
    }

    @Test
    public void testAllFoldersAnnotation()
    {
        final String sql = "SELECT Containers.Name FROM Containers[ContainerFilter='AllFolders']";
        String queryName = "testAllFoldersAnnotation";
        String[] expectedFolders = new String[]{getProjectName(), FOLDER_NAME, "Shared"};

        DataRegionTable table = createQuery(getProjectName(), queryName, "core", sql);
        List<String> containerNames = table.getColumnDataAsText("Name");
        assertThat("Wrong containers for 'AllFolders' container filter.", containerNames,
            hasItems(expectedFolders));

        table.setContainerFilter(DataRegionTable.ContainerFilterType.CURRENT_FOLDER);
        containerNames = table.getColumnDataAsText("Name");
        assertThat("ContainerFilter annotation should ignore data region container filter.", containerNames,
            hasItems(expectedFolders));

        table.setContainerFilter(DataRegionTable.ContainerFilterType.CURRENT_AND_SUBFOLDERS);
        containerNames = table.getColumnDataAsText("Name");
        assertThat("ContainerFilter annotation should ignore data region container filter.", containerNames,
            hasItems(expectedFolders));
    }

    @Test
    public void testContainerFilterAnnotationPermission()
    {
        final String sql = "SELECT Containers.Name FROM Containers[ContainerFilter='AllFolders']";
        String queryName = "testContainerFilterAnnotationPermission";

        ApiPermissionsHelper apiPermissionsHelper = new ApiPermissionsHelper(this);
        _containerHelper.createProject(OTHER_PROJECT);
        _userHelper.createUser(USER);
        apiPermissionsHelper
            .addMemberToRole(USER, "Reader", PermissionsHelper.MemberType.user, getFolderPath());

        DataRegionTable table = createQuery(getFolderPath(), queryName, "core", sql);
        impersonate(USER);
        List<String> visibleFolders = table.getColumnDataAsText("Name");
        assertThat("'AllFolders' doesn't respect permissions properly.", visibleFolders, allOf(
            hasItems(FOLDER_NAME, "Shared", "home"),
            not(hasItem(getProjectName())),
            not(hasItem(OTHER_PROJECT))
        ));
    }

    @NotNull
    public String getFolderPath()
    {
        return getProjectName() + "/" + FOLDER_NAME;
    }

    public DataRegionTable createQuery(String containerPath, String queryName, String targetSchema, String sql)
    {
        createQuery(containerPath, queryName, targetSchema, sql, null, false);
        clickButton("Save & Finish");
        return new DataRegionTable("query", getDriver());
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "ContainerFilterQueryTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList();
    }
}
