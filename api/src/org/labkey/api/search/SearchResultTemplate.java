/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
package org.labkey.api.search;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.search.SearchService.SearchCategory;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.util.List;

public interface SearchResultTemplate
{
    @Nullable String getName();

    /**
     * Return null for default behavior (using "category" parameter on the URL) or return a space-separated list of category names to override.
     */
    @Nullable String getCategories();

    /**
     * Return null for default behavior (using search scope on the URL) or return a search scope to override.
     */
    @Nullable SearchScope getSearchScope();

    @NotNull String getResultNameSingular();

    @NotNull String getResultNamePlural();

    boolean includeNavigationLinks();

    boolean includeAdvanceUI();

    @Nullable HtmlString getExtraHtml(ViewContext ctx);

    @Nullable HtmlString getHiddenInputsHtml(ViewContext ctx);

    String reviseQuery(ViewContext ctx, String q);

    default void addNavTrail(NavTree root, ViewContext ctx, @NotNull SearchScope scope, @Nullable String category)
    {
        Container c = ctx.getContainer();
        String title = "Search";

        switch (scope)
        {
            case All:
                title += " site";
                break;
            case Project:
                Container project = c.getProject();
                if (null != project)
                    title += " project '" + project.getName() + "'";
                break;
            case Folder:
            case FolderAndSubfolders:
                title += " folder '";
                if (c.getName().isEmpty())
                    title += "root'";
                else
                    title += c.getName() + "'";
                break;
        }

        SearchService ss = SearchService.get();
        if (ss != null)
        {
            List<SearchCategory> categories = ss.getCategories(category);

            if (null != categories)
            {
                List<String> list = categories.stream()
                    .map(SearchCategory::getDescription)
                    .toList();

                title += " for " + StringUtilsLabKey.joinWithConjunction(list, "and");
            }
        }

        root.addChild(title);
    }
}
