package org.labkey.test.tests.query;

import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locators;
import org.labkey.test.categories.Daily;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.APIContainerHelper;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PermissionsHelper;
import org.labkey.test.util.SchemaHelper;
import org.labkey.test.util.TestDataGenerator;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@Category({Daily.class})
public class ContainerFilterQueryTest extends BaseWebDriverTest
{
    private static final String OTHER_PROJECT = "Other ContainerFilterQueryTest Project"; // for permissions and linked schema tests
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
        _containerHelper.createProject(OTHER_PROJECT);
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
        List<String> expectedFolders = List.of(getProjectName(), FOLDER_NAME).stream().sorted().collect(Collectors.toList());

        DataRegionTable table = createQuery(getProjectName(), queryName, "core", sql);
        List<String> containerNames = table.getColumnDataAsText("Name").stream().sorted().collect(Collectors.toList());
        assertEquals("Wrong containers for 'CurrentAndSubfolders' container filter.",
            expectedFolders, containerNames);

        table.setContainerFilter(DataRegionTable.ContainerFilterType.CURRENT_FOLDER);
        containerNames = table.getColumnDataAsText("Name").stream().sorted().collect(Collectors.toList());
        assertEquals("ContainerFilter annotation should ignore data region container filter.", expectedFolders, containerNames);

        table.setContainerFilter(DataRegionTable.ContainerFilterType.ALL_FOLDERS);
        containerNames = table.getColumnDataAsText("Name").stream().sorted().collect(Collectors.toList());
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

    @Test
    public void testFolderSpecWithContainerFilter()
    {
        final String sql = "SELECT Containers.Name FROM \"/home/\".core.Containers[ContainerFilter='Current']";
        final String queryName = "testFolderSpecWithContainerFilter";

        DataRegionTable table = createQuery(getFolderPath(), queryName, "core", sql);
        table.setContainerFilter(DataRegionTable.ContainerFilterType.ALL_FOLDERS);
        List<String> containerNames = table.getColumnDataAsText("Name");
        assertEquals("Folder spec in query should be respected.",
            List.of("home"), containerNames);
    }

    @Test
    public void testContainerFilterOnLinkedSchema() throws IOException, CommandException
    {
        final String linkedSchemaName = "linkedLizts";
        final String listName = "myList";
        final String sql = String.format("SELECT * FROM %s.%s[ContainerFilter='AllFolders']", linkedSchemaName, listName);
        final String queryName = "testContainerFilterOnLinkedSchema";

        TestDataGenerator list1 = new TestDataGenerator("lists", listName, getFolderPath())
            .withColumns(List.of(
                new FieldDefinition("name", FieldDefinition.ColumnType.String)
            ))
            .addCustomRow(Map.of("name", "Geoffrey"))
            .addCustomRow(Map.of("name", "William"))
            .addCustomRow(Map.of("name", "James"));
        Connection connection = createDefaultConnection();
        list1.createList(connection, "Key");
        list1.insertRows(connection);

        new SchemaHelper(this).createLinkedSchema(getProjectName(), linkedSchemaName, getFolderPath(), null, "lists", null, null);

        DataRegionTable table = createQuery(getProjectName(), queryName, "linkedLizts", sql);
        List<String> names = table.getColumnDataAsText("Name").stream().sorted().collect(Collectors.toList());
        assertEquals("Wrong data in container filtered linked schema.", List.of("Geoffrey", "James", "William"), names);
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
