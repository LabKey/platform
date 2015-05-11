/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.WebPartView;

import java.util.Collection;

/**
 * User: Mark Igra
 * Date: Jun 12, 2006
 * Time: 2:48:54 PM
 */
public interface WikiService
{
    /** Name of the UserSchema exposed by the Wiki module */ 
    public static final String SCHEMA_NAME = "wiki";

    /** Name of the table exposed with the list of renderer types */
    public static final String RENDERER_TYPE_TABLE_NAME = "RendererType";

    public static final String WIKI_PREFIX = "<div class=\"labkey-wiki\">";
    public static final String WIKI_SUFFIX = "</div>";

    public WebPartView getView(Container c, String name, boolean renderContentOnly);
    public WebPartView getHistoryView(Container c, String name);

    public String getHtml(Container c, String name);

    @Deprecated // Use getView(Container c, String name, boolean renderContentOnly) instead (forceRefresh parameter is ignored)
    public WebPartView getView(Container c, String name, boolean forceRefresh, boolean renderContentOnly);

    @Deprecated // Use getHtml(Container c, String name) instead (forceRefresh parameter is ignored)
    public String getHtml(Container c, String name, boolean forceRefresh);

    public void insertWiki(User user, Container container, String name, String content, WikiRendererType renderType, String title);

    /**
     * Register a provider of macros.
     * @param name For macros of form {module:viewName} this is the module name
     * @param provider
     */
    public void registerMacroProvider(String name, MacroProvider provider);

    public String getFormattedHtml(WikiRendererType rendererType, String source);
    public String getFormattedHtml(WikiRendererType rendererType, String source, String attachPrefix, Collection<? extends Attachment> attachments);

    @Deprecated // use getFormattedHtml() -- service users shouldn't be exposed to WikiRenderer and FormattedHtml
    public WikiRenderer getRenderer(WikiRendererType rendererType);
    @Deprecated // use getFormattedHtml() -- service users shouldn't be exposed to WikiRenderer and FormattedHtml
    public WikiRenderer getRenderer(WikiRendererType rendererType, String attachPrefix, Collection<? extends Attachment> attachments);

    public WikiRendererType getDefaultWikiRendererType();
    public WikiRendererType getDefaultMessageRendererType();
    public java.util.List<String> getNames(Container c);

    public void addWikiListener(WikiChangeListener listener);
    public void removeWikiListener(WikiChangeListener listener);
}
