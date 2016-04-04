package org.labkey.test.tests.search;

import org.junit.experimental.categories.Category;
import org.labkey.test.categories.DailyA;
import org.labkey.test.util.search.SearchAdminAPIHelper;

@Category({DailyA.class})
public class SearchTestDefault extends SearchTest
{
    @Override
    public SearchAdminAPIHelper.DirectoryType directoryType()
    {
        return SearchAdminAPIHelper.DirectoryType.Default;
    }
}