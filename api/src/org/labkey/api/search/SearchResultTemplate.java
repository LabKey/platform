package org.labkey.api.search;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

/**
 * User: adam
 * Date: 2/18/12
 * Time: 10:13 AM
 */
public interface SearchResultTemplate
{
    public @Nullable String getName();

    // Return null for default behavior (using "category" parameter on the URL) or return a space-separated list of category names to override
    public @Nullable String getCategories();

    // Return null for default behavior (using search scope on the URL) or return a search scope to override
    public @Nullable SearchScope getSearchScope();

    public @NotNull String getResultName();

    public boolean includeAdvanceUI();

    public @Nullable String getExtraHtml(ViewContext ctx);

    public String reviseQuery(ViewContext ctx, String q);

    public NavTree appendNavTrail(NavTree root, ViewContext ctx, @NotNull SearchScope scope, @Nullable String category);
}
