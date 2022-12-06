/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import java.sql.SQLException;
import java.util.List;

/**
 * User: Mark Igra
 * Date: Jun 12, 2006
 * Time: 2:48:54 PM
 */
public interface WikiService
{
    static @Nullable WikiService get()
    {
        return ServiceRegistry.get().getService(WikiService.class);
    }

    static void setInstance(WikiService impl)
    {
        ServiceRegistry.get().registerService(WikiService.class, impl);
    }

    WebPartView getView(Container c, String name, boolean renderContentOnly);
    WebPartView getHistoryView(Container c, String name);

    HtmlString getHtml(Container c, String name);

    void insertWiki(User user, Container container, String name, String content, WikiRendererType renderType, String title);

    List<String> getNames(Container c);

    void addWikiListener(WikiChangeListener listener);
    void removeWikiListener(WikiChangeListener listener);

    void registerWikiPartFactory(WebPartFactory partFactory, WikiPartFactory.Privilege privilege, String activeModuleName);

    /** For columns that want a lookup to the canonical RendererType column. Be sure to null check WikiService before adding your FK. */
    TableInfo getRendererTypeTable(User user, Container container);

    /**
     * Returns the raw (unformatted) content
     */
    @Nullable String getContent(Container c, String wikiName);

    /**
     * Returns the raw (unformatted) content for every version of the wiki
     */
    @Nullable List<String> getAllContent(Container c, String wikiName);

    /**
     * Update the content of a wiki
     *
     * @param wikiName The name of the wiki to update
     * @param content The new String content
     * @param newVersionThreshold The interval in milliseconds since the last update that will trigger a new wiki version
     *                            to be created. This can be useful when frequent updates are made but we want to avoid
     *                            the proliferation of individual wiki versions.
     * @return
     */
    boolean updateContent(Container c, User user, String wikiName, String content, @Nullable Integer newVersionThreshold);

    void deleteWiki(Container c, User user, String wikiName, boolean deleteSubtree) throws SQLException;

    /**
     * Retrieve the attachment parent of a wiki
     */
    @Nullable
    AttachmentParent getAttachmentParent(Container c, User user, String wikiName);

    /**
     * Update the attachments on a wiki. Note, attachment changes do not update the wiki version.
     * If an error occurs, a non-null String will be returned containing the error message.
     */
    @Nullable
    String updateAttachments(Container c, User user, String wikiName, @Nullable List<AttachmentFile> attachmentFiles, @Nullable List<String> deleteAttachmentNames);
}
