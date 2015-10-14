/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
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
import org.openqa.selenium.SearchContext;
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
        @Override
        protected SearchContext getContext()
        {
            return getDriver();
        }

        final WebElement saveButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("Save"), this);
        final WebElement reloadNowButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("Reload Now"), this);
        final WebElement saveCompleteWindow = new LazyWaitingWebElement(Ext4Helper.Locators.window("Save Complete"), this);
        final WebElement errorWindow = new LazyWaitingWebElement(Ext4Helper.Locators.window("Failure"), this);
        final WebElement errorWindowMessage = new EphemeralWebElement(Locator.css("table.x4-window-text"), errorWindow);
        final WebElement errorWindowOkButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("OK"), errorWindow);

        final WebElement authenticationTab = new LazyWebElement(Ext4Helper.Locators.ext4Tab("Authentication"), this);
        final WebElement reloadingTab = new LazyWebElement(Ext4Helper.Locators.ext4Tab("Reloading"), this);
        final WebElement configurationSettingTab = new LazyWebElement(Ext4Helper.Locators.ext4Tab("Configuration Setting"), this);

        //AuthenticationTab
        final WebElement addTokenButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("+"), this);
        List<WebElement> deleteTokenButtons()
        {
            return Ext4Helper.Locators.ext4Button("delete").waitForElements(this, BaseWebDriverTest.WAIT_FOR_JAVASCRIPT);
        }
        List<WebElement> projectNameFields()
        {
            return Locator.tagWithName("input", "projectname").findElements(this);
        }
        List<WebElement> tokenFields()
        {
            return Locator.tagWithName("input", "token").findElements(this);
        }

        //ReloadingTab
        final WebElement enableReloadCheckbox = new LazyWebElement(Locator.id("enableReload"), this);
        final WebElement reloadDate = new LazyWebElement(Locator.name("reloadDate"), this);
        final WebElement reloadInterval = new LazyWebElement(Locator.name("reloadInterval"), this);

        //ConfigurationSettingTab
        final WebElement metadataTextArea = new LazyWebElement(Locator.css("textarea[name=metadata]"), this);
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
