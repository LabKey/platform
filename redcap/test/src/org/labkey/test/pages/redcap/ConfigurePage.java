package org.labkey.test.pages.redcap;

import com.google.common.base.Function;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.WebTestHelper;
import org.labkey.test.selenium.EphemeralWebElement;
import org.labkey.test.selenium.LazyWaitingWebElement;
import org.labkey.test.selenium.LazyWebElement;
import org.labkey.test.Locator;
import org.labkey.test.components.ComponentElements;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.util.Ext4Helper;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

public class ConfigurePage extends LabKeyPage
{
    private ConfigurePageTab _currentTab;
    private Elements _elements;
    private int _tokenCount;

    public ConfigurePage(BaseWebDriverTest test)
    {
        super(test);
        _currentTab = new AuthenticationTab();
        List<WebElement> deleteButtons = elements().deleteTokenButtons();
        _tokenCount = deleteButtons.get(0).getAttribute("class").contains("disabled") ? 0 : deleteButtons.size();
    }

    public static ConfigurePage beginAt(BaseWebDriverTest test, String containerPath)
    {
        test.beginAt(WebTestHelper.buildURL("redcap", containerPath, "configure"));
        return new ConfigurePage(test);
    }

    /**
     * Save config
     * @return null for success, otherwise the failure message
     */
    public String save()
    {
        _test.doAndWaitForPageSignal(
                elements().saveButton::click, "redcapSave");

        try
        {
            return elements().errorWindowMessage.getText();
        }
        catch (NoSuchElementException noError)
        {
            return null;
        }
    }

    public LabKeyPage reloadNow()
    {
        _test.clickAndWait(elements().reloadNowButton);
        _elements = null;

        return new LabKeyPage(_test);
    }

    public ConfigurePage deleteToken(int index)
    {
        selectAuthenticationTab();
        if (index >= _tokenCount)
            throw new IndexOutOfBoundsException(String.format("Index: %d Count: %d", index, _tokenCount));

        WebElement deleteButton = elements().deleteTokenButtons().get(index);
        deleteButton.click();

        _test.shortWait().until(ExpectedConditions.stalenessOf(deleteButton));

        _tokenCount--;

        return this;
    }

    public ConfigurePage addToken(String projectName, String token)
    {
        selectAuthenticationTab();
        if (_tokenCount > 0)
            elements().addTokenButton.click();

        _tokenCount++;
        return setToken(_tokenCount - 1, projectName, token);
    }

    public ConfigurePage setToken(int index, String projectName, String token)
    {
        selectAuthenticationTab();
        if (index >= _tokenCount)
            throw new IndexOutOfBoundsException(String.format("Index: %d Count: %d", index, _tokenCount));

        _test.setFormElement(elements().projectNameFields().get(index), projectName);
        _test.setFormElement(elements().tokenFields().get(index), token);

        return this;
    }

    public ConfigurePage setConfigurationXml(String xml)
    {
        selectConfigurationSettingTab();
        _test.setFormElement(elements().metadataTextArea, xml);

        return this;
    }

    private void selectAuthenticationTab()
    {
        _currentTab.selectAuthenticationTab();
    }

    private void selectReloadingTab()
    {
        _currentTab.selectReloadingTab();
    }

    private void selectConfigurationSettingTab()
    {
        _currentTab.selectConfigurationSettingTab();
    }

    private Elements elements()
    {
        if (null == _elements)
            _elements = new Elements(_test.getDriver());

        return _elements;
    }

    private class Elements extends ComponentElements
    {
        protected Elements(SearchContext context)
        {
            super(context);
        }

        WebElement saveButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("Save"), context);
        WebElement reloadNowButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("Reload Now"), context);
        WebElement saveCompleteWindow = new LazyWaitingWebElement(Ext4Helper.Locators.window("Save Complete"), context);
        WebElement errorWindow = new LazyWaitingWebElement(Ext4Helper.Locators.window("Failure"), context);
        WebElement errorWindowMessage = new EphemeralWebElement(Locator.css("table.x4-window-text"), errorWindow);
        WebElement errorWindowOkButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("OK"), errorWindow);

        //AuthenticationTab
        WebElement addTokenButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("+"), context);
        List<WebElement> deleteTokenButtons()
        {
            return Ext4Helper.Locators.ext4Button("delete").waitForElements(context, BaseWebDriverTest.WAIT_FOR_JAVASCRIPT);
        }
        List<WebElement> projectNameFields()
        {
            return findElements(Locator.tagWithName("input", "projectname"));
        }
        List<WebElement> tokenFields()
        {
            return findElements(Locator.tagWithName("input", "token"));
        }

        //ReloadingTab
        WebElement enableReloadCheckbox = new LazyWebElement(Locator.id("enableReload"), context);
        WebElement reloadDate = new LazyWebElement(Locator.name("reloadDate"), context);
        WebElement reloadInterval = new LazyWebElement(Locator.name("reloadInterval"), context);

        //ConfigurationSettingTab
        WebElement metadataTextArea = new LazyWebElement(Locator.css("textarea[name=metadata]"), context);
    }

    private abstract class ConfigurePageTab
    {
        void selectAuthenticationTab()
        {
            _test.click(elements().authenticationTab);
            _currentTab = new AuthenticationTab();
        }

        void selectReloadingTab()
        {
            _test.click(elements().reloadingTab);
            _currentTab = new ReloadingTab();
        }

        void selectConfigurationSettingTab()
        {
            _test.click(elements().configurationSettingTab);
            _currentTab = new ConfigurationSettingTab();
        }

        private Elements elements()
        {
            return new Elements();
        }

        private class Elements
        {
            private final Locator tab = Locator.tagWithClass("a", "x4-tab");
            final Locator authenticationTab = tab.withText("Authentication");
            final Locator reloadingTab = tab.withText("Reloading");
            final Locator configurationSettingTab = tab.withText("Configuration Setting");
        }
    }

    private class AuthenticationTab extends ConfigurePageTab
    {
        @Override
        void selectAuthenticationTab()
        {
            // Already there
        }
    }

    private class ReloadingTab extends ConfigurePageTab
    {
        @Override
        void selectReloadingTab()
        {
            // Already there
        }
    }

    private class ConfigurationSettingTab extends ConfigurePageTab
    {
        @Override
        void selectConfigurationSettingTab()
        {
            // Already there
        }
    }
}
