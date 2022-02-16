/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.products;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.HelpTopic;
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
        return "https://www.labkey.org/Documentation/wiki-page.view?name=default&referrer=" + HelpTopic.Referrer.docMenu;
    }

    public String getDocumentationLabel()
    {
        return "Documentation";
    }

    public @NotNull List<MenuItem> getUserMenuItems(ViewContext context)
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

    @Nullable
    public String getProductName()
    {
        return getProductId();
    }

    @NotNull
    public abstract Collection<String> getSectionNames(@Nullable ViewContext viewContext);

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
        return getSections(context, getSectionNames(context), itemLimit);
    }
}
