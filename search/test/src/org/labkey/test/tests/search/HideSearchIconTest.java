package org.labkey.test.tests.search;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.categories.Search;
import org.labkey.test.components.html.SiteNavBar;
import org.labkey.test.util.search.SearchAdminAPIHelper;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category({Search.class})
@BaseWebDriverTest.ClassTimeout(minutes = 1)
public class HideSearchIconTest extends BaseWebDriverTest
{
    @Override
    protected String getProjectName()
    {
        return null;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return List.of("Search");
    }

    @Test
    public void testHideSearchIcon()
    {
        goToHome();
        assertTrue(new SiteNavBar(getDriver()).isSearchIconPresent());

        // Hide icon, refresh page, and verify icon is gone
        SearchAdminAPIHelper.hideSearchIcon(getDriver());
        goToHome();
        assertFalse(new SiteNavBar(getDriver()).isSearchIconPresent());

        // Show icon, refresh page, and verify icon is back
        SearchAdminAPIHelper.showSearchIcon(getDriver());
        goToHome();
        assertTrue( new SiteNavBar(getDriver()).isSearchIconPresent());
    }
}

