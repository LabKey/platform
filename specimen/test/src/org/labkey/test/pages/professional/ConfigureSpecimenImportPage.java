package org.labkey.test.pages.professional;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.pages.study.ManageStudyPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PipelineStatusTable;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.Optional;

public class ConfigureSpecimenImportPage extends LabKeyPage<ConfigureSpecimenImportPage.ElementCache>
{
    public ConfigureSpecimenImportPage(WebDriver driver)
    {
        super(driver);
    }

    public static ConfigureSpecimenImportPage beginAt(WebDriverWrapper webDriverWrapper)
    {
        return beginAt(webDriverWrapper, webDriverWrapper.getCurrentContainerPath());
    }

    public static ConfigureSpecimenImportPage beginAt(WebDriverWrapper webDriverWrapper, String containerPath)
    {
        webDriverWrapper.beginAt(WebTestHelper.buildURL("specimen", containerPath, "configureQueryImport"));
        return new ConfigureSpecimenImportPage(webDriverWrapper.getDriver());
    }

    @Override
    protected void waitForPage()
    {
        WebDriverWrapper.waitFor(()-> elementCache().schemaSelect.isEnabled(),
                "The page did not become ready in time", WAIT_FOR_JAVASCRIPT);
    }

    public ConfigureSpecimenImportPage configureQueryBasedImport(boolean enableReload, String schema, String query, String view)
    {
        setCheckbox(elementCache().reloadBoxElement, enableReload);
        setFormElement(elementCache().schemaSelect, schema);
        setFormElement(elementCache().querySelect, query);
        setFormElement(elementCache().viewSelect, view);
        return this;
    }

    public ManageStudyPage clickSave()
    {
        clickAndWait(elementCache().saveButton);
        return new ManageStudyPage(getDriver());
    }

    public ManageStudyPage clickCancel()
    {
        clickAndWait(elementCache().cancelButton);
        return new ManageStudyPage(getDriver());
    }

    public boolean isReloadNowButtonPresent()
    {
        return elementCache().reloadNowButton().isPresent();
    }

    public DataRegionTable clickReloadNow()
    {
        doAndWaitForPageToLoad(()-> elementCache().reloadNowButton().get().click());
        return PipelineStatusTable.finder(getDriver()).waitFor();
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage<?>.ElementCache
    {
        WebElement cancelButton = Locator.linkWithSpan("Cancel").findWhenNeeded(getDriver());
        WebElement saveButton = Locator.linkWithSpan("Save").findWhenNeeded(getDriver());
        Optional<WebElement> reloadNowButton()
        {
            return Locator.linkWithSpan("Reload Now").findOptionalElement(getDriver());
        }

        WebElement formContainer = Locator.tagWithClass("div", "QBSpecimenImportFormFields")
                .findWhenNeeded(getDriver()).withTimeout(WAIT_FOR_JAVASCRIPT);

        WebElement reloadBoxElement = Locator.checkbox().withAttribute("name", "enabled")
                .findWhenNeeded(formContainer);
        WebElement schemaSelect = Locator.id("schemaName").findWhenNeeded(formContainer);
        WebElement querySelect = Locator.id("queryName").findWhenNeeded(formContainer);
        WebElement viewSelect = Locator.id("viewName").findWhenNeeded(formContainer);
    }
}
