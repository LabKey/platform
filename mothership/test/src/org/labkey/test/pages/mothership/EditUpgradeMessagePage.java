package org.labkey.test.pages.mothership;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.html.Input;
import org.labkey.test.selenium.LazyWebElement;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class EditUpgradeMessagePage extends BaseMothershipPage
{
    private Elements _elements;

    public EditUpgradeMessagePage(WebDriver driver)
    {
        super(driver);
    }

    public static EditUpgradeMessagePage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, MothershipHelper.MOTHERSHIP_PROJECT);
    }

    public static EditUpgradeMessagePage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("mothership", containerPath, "editUpgradeMessage"));
        return new EditUpgradeMessagePage(driver.getDriver());
    }

    public Input currentRevision()
    {
        return elements().currentRevisionInput;
    }

    public Input message()
    {
        return elements().messageTextArea;
    }

    public Input createIssueURL()
    {
        return elements().createIssueURLInput;
    }

    public Input issuesContainer()
    {
        return elements().issuesContainerInput;
    }

    public ShowExceptionsPage save()
    {
        clickAndWait(elements().saveButton);
        return new ShowExceptionsPage(getDriver());
    }

    protected Elements elements()
    {
        if (_elements == null)
            _elements = new Elements();
        return _elements;
    }

    private class Elements extends BaseMothershipPage.Elements
    {
        Input currentRevisionInput = new Input(new LazyWebElement(Locator.name("currentRevision"), this), getDriver());
        Input messageTextArea = new Input(new LazyWebElement(Locator.name("message"), this), getDriver());
        Input createIssueURLInput = new Input(new LazyWebElement(Locator.name("createIssueURL"), this), getDriver());
        Input issuesContainerInput = new Input(new LazyWebElement(Locator.name("issuesContainer"), this), getDriver());
        WebElement saveButton = new LazyWebElement(Locator.lkButton("Save"), this);
    }
}
