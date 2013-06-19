/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.wiki.model;

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;

/**
 * Context object for the wikiSearch.jsp view
 * User: Dave
 * Date: Oct 30, 2007
 * Time: 2:35:25 PM
 */
public class SearchViewContext
{
    public SearchViewContext(ViewContext ctx)
    {
        _searchUrl = ctx.getActionURL().clone();
        _searchUrl.setAction("search.view");
        _searchUrl.deleteParameters();
    }

    public String getSearchUrl()
    {
        return _searchUrl.getLocalURIString();
    }

    private ActionURL _searchUrl;

}
