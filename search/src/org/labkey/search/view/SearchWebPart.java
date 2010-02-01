/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.search.view;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.search.SearchController;

/**
 * User: adam
 * Date: Jan 19, 2010
 * Time: 1:59:42 PM
 */
public class SearchWebPart  extends JspView<SearchController.SearchForm>
{
    public SearchWebPart(String searchTerm, ActionURL searchUrl, boolean includeSubfolders, boolean showSettings)
    {
        this(searchTerm, searchUrl, includeSubfolders, showSettings, 40, false);
    }

    public SearchWebPart(String searchTerm, ActionURL searchUrl, boolean includeSubfolders, boolean showSettings, int textBoxWidth, boolean showExplanationText)
    {
        super("/org/labkey/search/view/search.jsp", new SearchController.SearchForm());

        setTitle("Search");
    }
}
