/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

/**
 * User: adam
 * Date: 2/18/12
 * Time: 10:13 AM
 */
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

    @Nullable String getExtraHtml(ViewContext ctx);

    @Nullable String getHiddenInputsHtml(ViewContext ctx);

    String reviseQuery(ViewContext ctx, String q);

    default NavTree appendNavTrail(NavTree root, ViewContext ctx, @NotNull SearchScope scope, @Nullable String category)
    {
        Container c = ctx.getContainer();
        String title = "Search";

        switch (scope)
        {
            case All:
                title += " site";
                break;
            case Project:
                title += " project '" + c.getProject().getName() + "'";
                break;
            case Folder:
            case FolderAndSubfolders:
                title += " folder '";
                if ("".equals(c.getName()))
                    title += "root'";
                else
                    title += c.getName() + "'";
                break;
        }

        if (null != category)
            title += " for " + category.replaceAll(" ", "s, ") + "s";

        return root.addChild(title);
    }
}
