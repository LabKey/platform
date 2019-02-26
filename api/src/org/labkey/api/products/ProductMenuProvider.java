package org.labkey.api.products;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class ProductMenuProvider
{
    @Nullable
    public String getDocumentationUrl()
    {
        return "https://www.labkey.org/Documentation/wiki-page.view?name=default";
    }

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

    @Nullable
    public abstract MenuSection getSection(@NotNull ViewContext context, @NotNull String sectionName, @Nullable Integer itemLimit);

    @NotNull
    public List<MenuSection> getSections(@NotNull ViewContext context, @NotNull Collection<String> sectionNames, @Nullable Integer itemLimit)
    {
        List<MenuSection> sections = new ArrayList<>();
        sectionNames.forEach((name) -> {
            MenuSection section = getSection(context, name, itemLimit);
            if (section != null)
                sections.add(section);
        });

        return sections;
    }

    @NotNull
    public List<MenuSection> getSections(@NotNull ViewContext context, Integer itemLimit)
    {
        return getSections(context, getSectionNames(), itemLimit);
    }
}
