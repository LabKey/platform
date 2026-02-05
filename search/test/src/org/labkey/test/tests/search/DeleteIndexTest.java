/*
 * Copyright (c) 2025 LabKey Corporation
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
package org.labkey.test.tests.search;

import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Search;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.SearchHelper;
import org.labkey.test.util.WikiHelper;
import org.labkey.test.util.search.SearchAdminAPIHelper;

import java.util.List;

@Category({Search.class})
@BaseWebDriverTest.ClassTimeout(minutes = 10)
public class DeleteIndexTest extends BaseWebDriverTest
{
    private static final String PROJECT_NAME = "DeleteIndexTest";
    private static final String UNIQUE_CONTENT = "Chrysoprase";  // Unique term unlikely to appear in other test content
    private final SearchHelper _searchHelper = new SearchHelper(this);

    @Override
    public List<String> getAssociatedModules()
    {
        return List.of("Search");
    }

    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @BeforeClass
    public static void setupProject()
    {
        DeleteIndexTest test = getCurrentTest();
        test._containerHelper.createProject(PROJECT_NAME, null);
        new PortalHelper(test).addWebPart("Wiki");
        new WikiHelper(test).createWikiPage("deleteIndexWiki", "RADEOX", "Delete Index Wiki", UNIQUE_CONTENT, null);
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        SearchAdminAPIHelper.startCrawler(getDriver());
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    @Test
    public void testDeleteIndex()
    {
        // Verify content is found in the index
        SearchAdminAPIHelper.waitForIndexerBackground();
        Assertions.assertThat(_searchHelper.searchFor(UNIQUE_CONTENT, false).getResultCount())
            .as("Content should be searchable after indexing")
            .isGreaterThan(0);

        // Pause the crawler so it won't re-index after deletion
        SearchAdminAPIHelper.pauseCrawler(getDriver());

        // Delete the index
        SearchAdminAPIHelper.deleteIndex(getDriver());

        // Verify content is no longer found
        Assertions.assertThat(_searchHelper.searchFor(UNIQUE_CONTENT, false).getResultCount())
            .as("Content should not be found after index deletion")
            .isEqualTo(0);

        // Resume the crawler and wait for content to be re-indexed
        SearchAdminAPIHelper.startCrawler(getDriver());
        int hitCount = 0;
        for (int attempt = 0; attempt < 10 && hitCount == 0; attempt++)
        {
            SearchAdminAPIHelper.waitForIndexerBackground();
            hitCount = _searchHelper.searchFor(UNIQUE_CONTENT, false).getResultCount();
        }
        Assertions.assertThat(hitCount)
            .as("Content should be found again after re-indexing")
            .isGreaterThan(0);
    }
}
