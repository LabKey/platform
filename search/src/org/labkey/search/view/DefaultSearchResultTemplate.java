package org.labkey.search.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.search.SearchResultTemplate;
import org.labkey.api.search.SearchScope;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

/**
 * User: adam
 * Date: 2/18/12
 * Time: 10:17 AM
 */
public class DefaultSearchResultTemplate implements SearchResultTemplate
{
    @Override
    public String getName()
    {
        return null;
    }

    @Override
    public String getCategories()
    {
        return null;
    }

    @Override
    public SearchScope getSearchScope()
    {
        return null;
    }

    @NotNull
    @Override
    public String getResultName()
    {
        return "result";
    }

    @Override
    public boolean includeAdvanceUI()
    {
        return true;
    }

    @Override
    public String getExtraHtml(ViewContext ctx)
    {
        return null;
    }

    @Override
    public String reviseQuery(ViewContext ctx, String q)
    {
        return q;
    }

    @Override
    public NavTree appendNavTrail(NavTree root, ViewContext ctx, @NotNull SearchScope scope, @Nullable String category)
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
