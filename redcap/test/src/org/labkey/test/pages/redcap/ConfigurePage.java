/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.test.pages.redcap;

import org.junit.Assert;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.ComponentElements;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.selenium.EphemeralWebElement;
import org.labkey.test.selenium.LazyWaitingWebElement;
import org.labkey.test.selenium.LazyWebElement;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

public class ConfigurePage extends LabKeyPage
{
    private ConfigurePageTab _currentTab;
    private final Elements _elements;
    private int _tokenCount;

    public ConfigurePage(BaseWebDriverTest test)
    {
        super(test);
        _elements = new Elements();
        _currentTab = new AuthenticationTab();
        List<WebElement> deleteButtons = elements().deleteTokenButtons();
        _tokenCount = deleteButtons.get(0).getAttribute("class").contains("disabled") ? 0 : deleteButtons.size();
    }

    public static ConfigurePage beginAt(BaseWebDriverTest test, String containerPath)
    {
        test.beginAt(WebTestHelper.buildURL("redcap", containerPath, "configure"));
        return new ConfigurePage(test);
    }

    @LogMethod(quiet = true)
    public SaveResult save()
    {
        return new SaveResult(_test.doAndWaitForPageSignal(elements().saveButton::click, "redcapSave"));
    }

    public class SaveResult
    {
        private boolean success;

        protected SaveResult(String result)
        {
            switch (result)
            {
                case "failure":
                    success = false;
                    break;
                case "success":
                    success = true;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown redcap save result: " + result);
            }
        }

        public ConfigurePage assertSuccess()
        {
            if (!success)
                Assert.fail(String.format("Save Failed: '%s'", elements().errorWindowMessage.getText()));

            return ConfigurePage.this;
        }

        public ConfigurePage assertFailure(String errorMessage)
        {
            Assert.assertFalse("Save succeeded", success);
            Assert.assertEquals("Wrong error", errorMessage, elements().errorWindowMessage.getText());
            elements().errorWindowOkButton.click();

            return ConfigurePage.this;
        }
    }

    @LogMethod(quiet = true)
    public LabKeyPage reloadNow()
    {
        _test.clickAndWait(elements().reloadNowButton);

        return new LabKeyPage(_test);
    }

    @LogMethod(quiet = true)
    public ConfigurePage deleteToken(@LoggedParam int index)
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

    @LogMethod(quiet = true)
    public ConfigurePage addToken(@LoggedParam String projectName, String token)
    {
        selectAuthenticationTab();
        if (_tokenCount > 0)
            elements().addTokenButton.click();

        _tokenCount++;
        return setToken(_tokenCount - 1, projectName, token);
    }

    @LogMethod(quiet = true)
    public ConfigurePage setToken(@LoggedParam int index, @LoggedParam String projectName, String token)
    {
        selectAuthenticationTab();
        if (index >= _tokenCount)
            throw new IndexOutOfBoundsException(String.format("Index: %d Count: %d", index, _tokenCount));

        _test.setFormElement(elements().projectNameFields().get(index), projectName);
        _test.setFormElement(elements().tokenFields().get(index), token);

        return this;
    }

    @LogMethod(quiet = true)
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
        return _elements;
    }

    private class Elements extends ComponentElements
    {
        protected Elements()
        {
            super(_test.getDriver());
        }

        final WebElement saveButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("Save"), context);
        final WebElement reloadNowButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("Reload Now"), context);
        final WebElement saveCompleteWindow = new LazyWaitingWebElement(Ext4Helper.Locators.window("Save Complete"), context);
        final WebElement errorWindow = new LazyWaitingWebElement(Ext4Helper.Locators.window("Failure"), context);
        final WebElement errorWindowMessage = new EphemeralWebElement(Locator.css("table.x4-window-text"), errorWindow);
        final WebElement errorWindowOkButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("OK"), errorWindow);

        final WebElement authenticationTab = new LazyWebElement(Ext4Helper.Locators.ext4Tab("Authentication"), context);
        final WebElement reloadingTab = new LazyWebElement(Ext4Helper.Locators.ext4Tab("Reloading"), context);
        final WebElement configurationSettingTab = new LazyWebElement(Ext4Helper.Locators.ext4Tab("Configuration Setting"), context);

        //AuthenticationTab
        final WebElement addTokenButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("+"), context);
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
        final WebElement enableReloadCheckbox = new LazyWebElement(Locator.id("enableReload"), context);
        final WebElement reloadDate = new LazyWebElement(Locator.name("reloadDate"), context);
        final WebElement reloadInterval = new LazyWebElement(Locator.name("reloadInterval"), context);

        //ConfigurationSettingTab
        final WebElement metadataTextArea = new LazyWebElement(Locator.css("textarea[name=metadata]"), context);
    }

    private abstract class ConfigurePageTab
    {
        void selectAuthenticationTab()
        {
            elements().authenticationTab.click();
            _currentTab = new AuthenticationTab();
        }

        void selectReloadingTab()
        {
            elements().reloadingTab.click();
            _currentTab = new ReloadingTab();
        }

        void selectConfigurationSettingTab()
        {
            elements().configurationSettingTab.click();
            _currentTab = new ConfigurationSettingTab();
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
