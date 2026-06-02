/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.api.wiki;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public interface WikiRenderingService
{
    HtmlString WIKI_PREFIX = HtmlString.unsafe("<div class=\"labkey-wiki\">");
    HtmlString WIKI_SUFFIX = HtmlString.unsafe("</div>");

    // How should substitutions be handled? Substitutions are allowed in HTML wikis only.
    enum SubstitutionMode
    {
        Ignore, // Don't substitute, leaving any substitution tokens as-is
        Remove, // Substitute with blanks, ensuring that all substitution tokens are removed
        Substitute // Substitute normally, swapping substitution tokens with their replacements
    }

    static @NotNull WikiRenderingService get()
    {
        return Objects.requireNonNull(ServiceRegistry.get().getService(WikiRenderingService.class));
    }

    static void setInstance(WikiRenderingService impl)
    {
        ServiceRegistry.get().registerService(WikiRenderingService.class, impl);
    }

    /**
     * Register a provider of macros.
     * @param name For macros of form {module:viewName} this is the module name
     * @param provider A macro provider
     */
    void registerMacroProvider(String name, MacroProvider provider);

    /**
     * @param sourceDescription info on where the text came from for debugging purposes. For example: Announcement 6654 in /MyContainer
     */
    HtmlString getFormattedHtml(WikiRendererType rendererType, String source, @Nullable String sourceDescription);

    /**
     * @param sourceDescription info on where the text came from for debugging purposes. For example: Announcement 6654 in /MyContainer
     * @param substitutionMode determines how webpart and dependency substitutions ({@code ${labkey.webPart}} and {@code ${labkey.dependency}} are handled)
     */
    HtmlString getFormattedHtml(WikiRendererType rendererType, String source, @Nullable String sourceDescription,
                                SubstitutionMode substitutionMode, String attachPrefix, Collection<? extends Attachment> attachments);

    /**
     * @param sourceDescription info on where the text came from for debugging purposes. For example: Announcement 6654 in /MyContainer
     * @param substitutionMode determines how webpart and dependency substitutions ({@code ${labkey.webPart}} and {@code ${labkey.dependency}} are handled)
     */
    WikiRenderer getRenderer(WikiRendererType rendererType, SubstitutionMode substitutionMode, String hrefPrefix,
                             String attachPrefix, Map<String, String> nameTitleMap,
                             Collection<? extends Attachment> attachments,
                             String sourceDescription);
}
