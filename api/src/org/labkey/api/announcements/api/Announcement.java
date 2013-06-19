/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.wiki.WikiRendererType;

import java.util.Collection;
import java.util.Date;

/**
 * User: Nick
 * Date: Jun 30, 2010
 * Time: 6:00:00 PM
 */
public interface Announcement
{
    public String getTitle();
    public String getBody();
    public Date getExpires();
    public int getRowId();
    public Container getContainer();
    public Collection<Attachment> getAttachments();
    public String getStatus();
    public Date getCreated();
    public Date getModified();
    public WikiRendererType getRendererType();
}
