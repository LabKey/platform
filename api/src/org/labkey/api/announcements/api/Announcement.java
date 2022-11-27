/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
package org.labkey.api.announcements.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.wiki.WikiRendererType;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * User: Nick
 * Date: Jun 30, 2010
 * Time: 6:00:00 PM
 */
public interface Announcement
{
    String getTitle();
    String getBody();
    Date getExpires();
    int getRowId();
    String getEntityId();
    Container getContainer();
    Collection<Attachment> getAttachments();
    String getStatus();
    Date getCreated();
    Date getModified();
    WikiRendererType getRendererType();
    @NotNull List<Integer> getMemberListIds();
}
