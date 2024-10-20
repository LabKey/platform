/*
 * Copyright (c) 2016-2018 LabKey Corporation
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

import org.junit.experimental.categories.Category;
import org.labkey.test.categories.Disabled;
import org.labkey.test.util.search.SearchAdminAPIHelper;

/**
 * 'Default' is redundant with whatever the default is (MMapDirectory).
 * Don't include in any suite by default. Retain to allow running manually.
 */
@Category({Disabled.class})
public class SearchTestDefault extends SearchTest
{
    @Override
    public SearchAdminAPIHelper.DirectoryType directoryType()
    {
        return SearchAdminAPIHelper.DirectoryType.Default;
    }
}
