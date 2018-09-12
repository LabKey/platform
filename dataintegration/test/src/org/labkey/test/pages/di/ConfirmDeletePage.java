package org.labkey.test.pages.di;

import org.labkey.test.Locator;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * User: tgaluhn
 * Date: 6/21/2018
 */
public class ConfirmDeletePage extends LabKeyPage<ConfirmDeletePage.ElementCache>
{

    public ConfirmDeletePage(WebDriver driver)
    {
        super(driver);
    }

    public LabKeyPage confirmDelete()
    {
        elementCache().confirmDeleteButton.click();
        return new LabKeyPage(getDriver());
    }

    public LabKeyPage cancel()
    {
        elementCache().cancelButton.click();
        return new LabKeyPage(getDriver());
    }

    public void assertConfirmation(String... names)
    {
        List<String> selectedNames = new ArrayList<>(Arrays.asList(names));
        selectedNames.removeIf(name -> null != Locator.tagWithText("li", name).findElementOrNull(getDriver()));
        assertEquals("Not all selected definitions were included in confirmation.", Collections.emptyList(), selectedNames);
    }

    public void assertConfirmationRespectEnabled(String name, boolean enabled)
    {
        final String enabledCheck = "This definition has been enabled and is scheduled to run.";
        final String scheduledList = "scheduledEtls";
        final String unscheduledList = "unscheduledEtls";
        String correctList;
        String incorrectList;

        if (enabled)
        {
            assertTextPresent(enabledCheck);
            correctList = scheduledList;
            incorrectList = unscheduledList;
        }
        else
        {
            assertTextNotPresent(enabledCheck);
            correctList = unscheduledList;
            incorrectList = scheduledList;
        }
        assertElementPresent(Locator.tagWithId("ul", correctList).withChild(Locator.tagWithText("li", name)));
        assertElementNotPresent(Locator.tagWithId("ul", incorrectList).withChild(Locator.tagWithText("li", name)));
    }

    protected ConfirmDeletePage.ElementCache newElementCache()
    {
        return new ConfirmDeletePage.ElementCache();
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
        protected WebElement name = Locator.id("name").findWhenNeeded(this);
        protected WebElement confirmDeleteButton = Locator.lkButton("Confirm Delete").findWhenNeeded(this);
        protected WebElement cancelButton = Locator.lkButton("Cancel").findWhenNeeded(this);
    }
}
