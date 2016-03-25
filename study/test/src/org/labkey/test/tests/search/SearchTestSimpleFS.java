package org.labkey.test.tests.search;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.categories.DailyA;
import org.labkey.test.util.search.SearchAdminAPIHelper;

@Category({DailyA.class})
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