/*
 * Copyright (c) 2007 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.announcements.model.Announcement;
import org.labkey.announcements.model.AnnouncementManager.*;
import org.labkey.api.security.User;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;

/**
 * User: adam
 * Date: Mar 4, 2007
 * Time: 9:53:16 PM
 */
public class DailyDigestEmailPrefsSelector extends EmailPrefsSelector
{
    Map<User, EmailPref> _epMap;

    protected DailyDigestEmailPrefsSelector(Container c) throws SQLException
    {
        super(c);

        // Create a map for shouldSend()
        _epMap = new HashMap<User, EmailPref>(_emailPrefs.size());

        for (AnnouncementManager.EmailPref ep : _emailPrefs)
            _epMap.put(ep.getUser(), ep);
    }


    @Override
    protected boolean includeEmailPref(AnnouncementManager.EmailPref ep)
    {
        return super.includeEmailPref(ep) && ((ep.getEmailOptionId() & AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST) != 0);
    }


    public boolean shouldSend(Announcement ann, User user) throws SQLException, ServletException
    {
        return shouldSend(ann, _epMap.get(user));
    }
}
