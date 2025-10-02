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

package org.labkey.announcements.model;

import org.apache.commons.lang3.StringUtils;
import org.labkey.announcements.api.AnnouncementImpl;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.ActionURL;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class DiscussionServiceImpl implements DiscussionService
{
    public static ActionURL fromSaved(String saved)
    {
        if (saved.startsWith("~/"))
            saved = AppProps.getInstance().getContextPath() + saved.substring(1);
        ActionURL url = new ActionURL(saved);
        String id = StringUtils.strip(url.getExtraPath(), "/");
        Container c = ContainerManager.getForId(id);
        if (null != c)
            url.setContainer(c);
        return url;
    }

    @Override
    public Collection<? extends Announcement> getDiscussions(Container container, String identifier, boolean includeResponses)
    {
        final List<Announcement> ret = new LinkedList<>();

        for (AnnouncementModel ann : AnnouncementManager.getDiscussions(container, identifier))
        {
            ret.add(new AnnouncementImpl(ann));

            if (includeResponses)
                ann.getResponses().forEach(x -> ret.add(new AnnouncementImpl(x)));
        }

        return ret;
    }

    @Override
    public DiscussionService.Settings getSettings(Container container)
    {
        try
        {
            return AnnouncementManager.getMessageBoardSettings(container);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setSettings(Container container, DiscussionService.Settings settings)
    {
        try
        {
            AnnouncementManager.saveMessageBoardSettings(container, settings);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
