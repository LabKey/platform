package org.labkey.test.pages.di;

import org.jetbrains.annotations.Nullable;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.util.DataRegionTable;
import org.openqa.selenium.WebDriver;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * User: tgaluhn
 * Date: 6/21/2018
 */
public class DefinitionsQueryView extends LabKeyPage<DefinitionsQueryView.ElementCache>
{
    public DefinitionsQueryView(WebDriver driver)
    {
        super(driver);
    }

    public static DefinitionsQueryView beginAt(WebDriverWrapper driver)
    {
        driver.beginAt(driver.getCurrentContainerPath());
        driver.goToFolderManagement().selectTab("etls");
        return new DefinitionsQueryView(driver.getDriver());
    }

    public LabKeyPage createNew(String definitionXml)
    {
        return createNew(definitionXml, null);
    }

    public LabKeyPage createNew(String definitionXml, @Nullable String expectedError)
    {
        elementCache()._dataRegionTable.clickInsertNewRow();
        return new DefinitionPage(getDriver()).setDefinitionXml(definitionXml).save(expectedError);
    }

    public DefinitionPage createNewWithNameConflict(String definitionXml)
    {
        final String NAME_CONFLICT_TITLE = "Definition Name Conflict";
        final String NAME_CONFLICT_MESSAGE = "This definition name is already in use in the current folder. Please specify a different name.";
        elementCache()._dataRegionTable.clickInsertNewRow();
        return new DefinitionPage(getDriver()).setDefinitionXml(definitionXml).saveWithModal(NAME_CONFLICT_TITLE, NAME_CONFLICT_MESSAGE);
    }

    public DefinitionPage edit(String name)
    {
        elementCache()._dataRegionTable.clickEditRow(getRowIndex(name));
        return new DefinitionPage(getDriver());
    }
    public LabKeyPage editAndSave(String name, String definitionXml, @Nullable String expectedError)
    {
        return edit(name).setDefinitionXml(definitionXml).save(expectedError);
    }

    public DefinitionPage details(String name)
    {
        elementCache()._dataRegionTable.clickRowDetails(getRowIndex(name));
        return new DefinitionPage(getDriver());
    }

    public ConfirmDeletePage delete(String... names)
    {
        Arrays.stream(names).forEach(name -> elementCache()._dataRegionTable.checkCheckbox(getRowIndex(name)));
        elementCache()._dataRegionTable.clickHeaderButton("Delete");
        ConfirmDeletePage deletePage = new ConfirmDeletePage(getDriver());
        deletePage.assertConfirmation(names);
        return deletePage;
    }

    public ConfirmDeletePage deleteWithEnabledCheck(String name, boolean enabled)
    {
        elementCache()._dataRegionTable.checkCheckbox(getRowIndex(name));
        elementCache()._dataRegionTable.clickHeaderButton("Delete");
        ConfirmDeletePage deletePage = new ConfirmDeletePage(getDriver());
        deletePage.assertConfirmationRespectEnabled(name, enabled);
        return deletePage;
    }

    public boolean isEtlPresent(String name)
    {
        return getRowIndex(name) > -1;
    }

    public void assertEtlPresent(String name)
    {
        assertTrue("Etl with name '" + name + "' not present in grid", isEtlPresent(name));
    }

    public void assertEtlNotPresent(String name)
    {
        assertEquals("Etl with name '" + name + "' present in grid ", getRowIndex(name), -1);
    }

    public String getRowPk(String name)
    {
        beginAt(this);
        if (elementCache()._dataRegionTable.getColumnIndex("EtlDefId") < 0)
        {
            CustomizeView helper = elementCache()._dataRegionTable.getCustomizeView();
            helper.openCustomizeViewPanel();
            helper.showHiddenItems();
            helper.addColumn("EtlDefId");
            helper.saveCustomView();
        }
        return elementCache()._dataRegionTable.getRowDataAsMap(getRowIndex(name)).get("EtlDefId");
    }

    private int getRowIndex(String name)
    {
        return elementCache()._dataRegionTable.getRowIndex("Name", name);
    }

    protected DefinitionsQueryView.ElementCache newElementCache()
    {
        return new DefinitionsQueryView.ElementCache();
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
        DataRegionTable _dataRegionTable = new DataRegionTable.DataRegionFinder(getDriver()).withName("transforms").findWhenNeeded(this);
    }

}
