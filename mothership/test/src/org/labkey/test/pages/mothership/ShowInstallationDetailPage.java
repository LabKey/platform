/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.test.pages.mothership;

import org.apache.commons.lang3.text.WordUtils;
import org.labkey.test.LabKeySiteWrapper;
import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.WebDriver;

import java.util.Collections;

public class ShowInstallationDetailPage extends LabKeyPage<ShowInstallationDetailPage.ElementCache>
{
    public ShowInstallationDetailPage(WebDriver driver)
    {
        super(driver);
    }

    public static ShowInstallationDetailPage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, MothershipHelper.MOTHERSHIP_PROJECT);
    }

    public static ShowInstallationDetailPage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("mothership", containerPath, "showInstallationDetail", Collections.singletonMap("serverInstallationId", "1")));
        return new ShowInstallationDetailPage(driver.getDriver());
    }

    public String getDistributionName()
    {
        return getInstallationValue("Distribution");
    }

    public String getServerIP()
    {
        return getInstallationValue("Server IP");
    }

    public String getServerHostName()
    {
        return getInstallationValue("Server Host Name");
    }

    public String getInstallationValue(String labelText)
    {
        return Locator.tagWithClassContaining("td", "lk-form-label")
                .withText(WordUtils.capitalize(labelText))  // TODO: WordUtils is deprecated in Commons Lang 3.6; this class is now maintained in Commons Text
                .followingSibling("td")
                .findElement(getDriver()).getText();
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
    }
}