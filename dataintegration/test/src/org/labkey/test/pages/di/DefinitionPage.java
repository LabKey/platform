package org.labkey.test.pages.di;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.labkey.test.Locator;
import org.labkey.test.components.labkey.LabKeyAlert;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

/**
 * User: tgaluhn
 * Date: 6/21/2018
 */
public class DefinitionPage extends LabKeyPage<DefinitionPage.ElementCache>
{
    private static final String DEFINITION_ID = "etlDefinition";

    public DefinitionPage(WebDriver driver)
    {
        super(driver);
    }

    public DefinitionPage setDefinitionXml(String definitionXml)
    {
        waitForElementToBeVisible(Locator.tagWithClass("div", "CodeMirror"));
        setCodeEditorValue(DEFINITION_ID, definitionXml);
        return new DefinitionPage(getDriver());
    }

    public String getDefinitionXml()
    {
        return _extHelper.getCodeMirrorValue(DEFINITION_ID);
    }

    public LabKeyPage save()
    {
        return save(null);
    }

    public LabKeyPage save(@Nullable String expectedError)
    {
        elementCache().saveButton.click();
        if (null != expectedError)
            assertTextPresent(expectedError);

        return new LabKeyPage(getDriver());
    }

    public DefinitionPage saveWithModal(String expectedModalTitle, @Nullable String expectedModalMessage)
    {
        clickAndWait(elementCache().saveButton);
        LabKeyAlert modal = new LabKeyAlert(getDriver());

        Assert.assertEquals(modal.getTitle(), expectedModalTitle);
        if (null != expectedModalMessage)
            Assert.assertEquals(modal.getBodyText(), expectedModalMessage);

        modal.close();
        return new DefinitionPage(getDriver());
    }

    public LabKeyPage saveChangedName(boolean asNewDefinition)
    {
        clickAndWait(elementCache().saveButton);
        LabKeyAlert modal = new LabKeyAlert(getDriver());
        Assert.assertEquals(modal.getTitle(), "Definition Name Changed");

        if (asNewDefinition)
            clickAndWait(Locator.linkWithText("Save As New"));
        else
            clickAndWait(Locator.linkWithText("Update Existing"));

        return new LabKeyPage(getDriver());
    }

    public DefinitionPage copyFromExisting(@NotNull String containerPath, @NotNull String definitionName)
    {
        elementCache().copyFromExistingButton.click();
        LabKeyAlert modal = new LabKeyAlert(getDriver());
        new Select(elementCache().templateContainerSelect).selectByValue(containerPath);
        sleep(WAIT_FOR_JAVASCRIPT); //wait for ajax
        new Select(elementCache().templateDefinitionSelect).selectByVisibleText(definitionName);
        elementCache().applyCopyButton.click();


        return new DefinitionPage(getDriver());
    }

    public LabKeyPage cancel()
    {
        elementCache().cancelButton.click();
        return new LabKeyPage(getDriver());
    }

    public DefinitionPage edit()
    {
        elementCache().editButton.click();
        return new DefinitionPage(getDriver());
    }

    public DefinitionsQueryView showGrid()
    {
        elementCache().showGridButton.click();
        return new DefinitionsQueryView(getDriver());
    }

    protected DefinitionPage.ElementCache newElementCache()
    {
        return new DefinitionPage.ElementCache();
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
        protected WebElement name = Locator.id("name").findWhenNeeded(this);
        protected WebElement saveButton = Locator.lkButton("Save").findWhenNeeded(this);
        protected WebElement cancelButton = Locator.lkButton("Cancel").findWhenNeeded(this);
        protected WebElement editButton = Locator.lkButton("Edit").findWhenNeeded(this);
        protected WebElement showGridButton = Locator.lkButton("Show Grid").findWhenNeeded(this);
        protected WebElement copyFromExistingButton = Locator.id("chooseTemplateButton").findWhenNeeded(this);
        protected WebElement templateContainerSelect = Locator.id("template-container-select").findWhenNeeded(this);
        protected WebElement templateDefinitionSelect = Locator.id("template-select").findWhenNeeded(this);
        protected WebElement applyCopyButton = Locator.id("template-apply-btn").findWhenNeeded(this);

    }
}
