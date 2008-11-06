/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.wiki;

import org.labkey.api.util.Search;
import org.labkey.api.util.SearchHit;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Set;
import java.util.List;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 10:54:33 AM
 */
public class WikiSearchable implements Search.Searchable
{
    public static final String SEARCH_DOMAIN = "wiki";

    public void search(Search.SearchTermParser parser, Set<Container> containers, List<SearchHit> hits, User user)
    {
        WikiManager.search(parser, containers, hits);
    }

    public String getSearchResultNamePlural()
    {
        return "Wiki Pages";
    }

    public String getDomainName()
    {
        return SEARCH_DOMAIN;
    }
}
