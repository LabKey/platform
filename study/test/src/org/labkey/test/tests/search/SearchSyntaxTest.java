package org.labkey.test.tests.search;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.DailyC;
import org.labkey.test.util.SearchHelper;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

@Category({DailyC.class})
public class SearchSyntaxTest extends BaseWebDriverTest
{
    @Test
    public void testSyntaxErrorMessages()
    {
        SearchHelper searchHelper = new SearchHelper(this);
        searchHelper.searchFor("age()", false);
        checkSyntaxErrorMessage("Error: Can't parse 'age()': Problem character is highlighted", "These characters have special meaning within search queries:", "You can escape special characters using \\ before the character or you can enclose the query string in double quotes.", "For more information, visit the search syntax documentation.");
        searchHelper.searchFor("incomplete(", false);
        checkSyntaxErrorMessage("Error: Can't parse 'incomplete(': Query string is incomplete", "These characters have special meaning within search queries:");
        searchHelper.searchFor("this AND OR", false);
        checkSyntaxErrorMessage("Error: Can't parse 'this AND OR': Problem character is highlighted", "Boolean operators AND, OR, and NOT have special meaning within search queries");
    }

    private void checkSyntaxErrorMessage(String... expectedPhrases)
    {
        String errorText = getText(Locator.css("div.alert-warning table"));
        // We want our nice, custom error messages to appear
        for (String phrase : expectedPhrases)
        {
            assertTrue("Did not find expected error message: " + phrase, errorText.contains(phrase));
        }

        // Various phrases that appear in the standard Lucene system error message
        assertTextNotPresent("Cannot parse", "encountered", "Was expecting", "<NOT>", "<OR>", "<AND>", "<EOF>");
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return null;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("search");
    }
}