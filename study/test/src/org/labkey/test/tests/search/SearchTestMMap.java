package org.labkey.test.tests.search;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.categories.DailyB;
import org.labkey.test.util.search.SearchAdminAPIHelper;

@Category({DailyB.class})
public class SearchTestMMap extends SearchTest
{
    @Test
    public void testMMapDirectorySearch()
    {
        SearchAdminAPIHelper.setDirectoryType(SearchAdminAPIHelper.DirectoryType.MMapDirectory);
        doCreateSteps();
        doVerifySteps();
    }
}