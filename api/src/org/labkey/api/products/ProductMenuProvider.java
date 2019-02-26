package org.labkey.api.products;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class ProductMenuProvider
{
    @Nullable
    public abstract ActionURL getDocumentationUrl();

    public @NotNull List<MenuItem> getUserMenuItems()
    {
        return Collections.emptyList();
    }

    public @NotNull List<MenuItem> getDevMenuItems()
    {
        return Collections.emptyList();
    }

    @NotNull
    public abstract String getModuleName();

    @NotNull
    public abstract String getProductId();

    @NotNull
    public abstract Collection<String> getSectionNames();

    @NotNull
    public abstract List<MenuSection> getSections(@NotNull ViewContext context, @NotNull Collection<String> sectionNames, Integer itemLimit);

    @NotNull
    public abstract List<MenuSection> getSections(@NotNull ViewContext context, Integer itemLimit);
}
