package org.labkey.test.tests.search;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.categories.DailyA;
import org.labkey.test.util.search.SearchAdminAPIHelper;

@Category({DailyA.class})
public class SearchTestNIOFS extends SearchTest
{
    @Test
    public void testNIOFSDirectory()
    {
        SearchAdminAPIHelper.setDirectoryType(SearchAdminAPIHelper.DirectoryType.NIOFSDirectory, getDriver());
        doCreateSteps();
        doVerifySteps();
    }
}