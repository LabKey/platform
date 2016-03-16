package org.labkey.test.tests.search;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.categories.DailyB;
import org.labkey.test.util.search.SearchAdminAPIHelper;

@Category({DailyB.class})
public class SearchTestSimpleFS extends SearchTest
{
    @Test
    public void testSimpleFSDirectory()
    {
        SearchAdminAPIHelper.setDirectoryType(SearchAdminAPIHelper.DirectoryType.SimpleFSDirectory);
        doCreateSteps();
        doVerifySteps();
    }

}