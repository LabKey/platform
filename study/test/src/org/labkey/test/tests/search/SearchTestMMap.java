package org.labkey.test.tests.search;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.categories.DailyA;
import org.labkey.test.util.search.SearchAdminAPIHelper;

@Category({DailyA.class})
public class SearchTestMMap extends SearchTest
{
    @Test
    public void testMMapDirectorySearch()
    {
        SearchAdminAPIHelper.setDirectoryType(SearchAdminAPIHelper.DirectoryType.MMapDirectory, getDriver());
        doCreateSteps();
        doVerifySteps();
    }
}