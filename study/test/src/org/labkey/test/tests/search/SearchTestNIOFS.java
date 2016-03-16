package org.labkey.test.tests.search;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.categories.DailyB;
import org.labkey.test.util.search.SearchAdminAPIHelper;

@Category({DailyB.class})
public class SearchTestNIOFS extends SearchTest
{
    @Test
    public void testNIOFSDirectory()
    {
        SearchAdminAPIHelper.setDirectoryType(SearchAdminAPIHelper.DirectoryType.NIOFSDirectory);
        doCreateSteps();
        doVerifySteps();
    }
}