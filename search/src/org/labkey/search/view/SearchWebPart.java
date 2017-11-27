/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.labkey.api.view.JspView;
import org.labkey.search.SearchController;
import org.labkey.search.SearchController.SearchForm;

/**
 * User: adam
 * Date: Jan 19, 2010
 * Time: 1:59:42 PM
 */
public class SearchWebPart extends JspView<SearchController.SearchForm>
{
    public SearchWebPart(boolean includeSubfolders, int textBoxWidth, boolean includeHelpLink, boolean isWebPart)
    {
        super("/org/labkey/search/view/search.jsp", getForm(includeSubfolders, textBoxWidth, includeHelpLink, isWebPart));
        setTitle("Search");
    }

    private static SearchController.SearchForm getForm(boolean includeSubfolders, int textBoxWidth, boolean includeHelpLink, boolean isWebpart)
    {
        SearchForm form = new SearchForm();
        form.setTextBoxWidth(textBoxWidth);
        form.setIncludeHelpLink(includeHelpLink);
        form.setWebPart(isWebpart);

        // This mimics the old search webpart.  TODO: Customize should allow picking scope.
        form.setScope(includeSubfolders ? "FolderAndSubfolders" : "Folder");

        return form;
    }
}
