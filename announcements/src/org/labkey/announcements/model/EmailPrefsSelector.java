/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.announcements.EmailOption;
import org.labkey.api.data.Container;
import org.labkey.api.message.settings.MessageConfigService.UserPreference;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.HttpView;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Mar 4, 2007
 * Time: 9:14:51 PM
 */
public abstract class EmailPrefsSelector
{
    // This map contains one or more email preferences for each user who has read permissions to this folder.  If the
    // user has not indicated a preference then their map includes a preference with the folder default option.
    private final Map<User, PreferencePicker> _emailPrefsMap;
    private final Container _c;

    protected EmailPrefsSelector(Container c)
    {
        _emailPrefsMap = createEmailPrefsMap(c);
        _c = c;
    }


    // Get all settings from the database, add the default values, and create map of User -> PreferencePicker
    private Map<User, PreferencePicker> createEmailPrefsMap(Container c)
    {
        int defaultOption = AnnouncementManager.getDefaultEmailOption(c);
        UserPreference[] upArray = AnnouncementManager.getAnnouncementConfigProvider().getPreferences(c);
        Map<User, PreferencePicker> map = new HashMap<>();

        for (UserPreference up : upArray)
        {
            if (null == up.getEmailOptionId())
                up.setEmailOptionId(defaultOption);

            User user = up.getUser();
            PreferencePicker pp = map.get(user);

            if (null == pp)
            {
                pp = new PreferencePicker(c);
                map.put(user, pp);
            }

            pp.addPreference(up);
        }

        return map;
    }


    // All users with read permissions in this folder... not filtered for anything else!
    public Collection<User> getNotificationCandidates()
    {
        return _emailPrefsMap.keySet();
    }


    // Anything but NONE... override this to filter out other prefs
    protected boolean includeEmailPref(UserPreference ep)
    {
        return EmailOption.MESSAGES_NONE.getValue() != ep.getEmailOptionId();
    }


    public boolean shouldSend(@Nullable AnnouncementModel ann, User user)
    {
        PreferencePicker pp = _emailPrefsMap.get(user);
        UserPreference up = pp.getApplicablePreference(ann);

        // This should not happen, but it has on one discussion board attached to a list, see #15748 & #15731. For now,
        // log information to mothership (to help track this down) and return false (to avoid subsequent NPE).
        if (null == up)
        {
            ExceptionUtil.logExceptionToMothership(HttpView.currentRequest(), new IllegalStateException("UserPreference is null for user: " + user.getEmail() + ", ann: " + (null != ann ? ann.getRowId() : null) + ", c: " + _c.toString() + "\n" + pp.toString()));
            return false;
        }

        // Skip if current notification type (e.g., individual or digest) doesn't match the preference
        if (!includeEmailPref(up))
            return false;

        // Skip if user is inactive
        if (!user.isActive())
            return false;

        DiscussionService.Settings settings = AnnouncementsController.getSettings(_c);
        int emailPreference = up.getEmailOptionId();

        if (EmailOption.MESSAGES_MINE.getValue() == emailPreference)
        {
            // Skip if preference is MINE and this is a new message  TODO: notify message creator?
            if (null == ann)
                return false;

            Set<User> authors = ann.getAuthors();

            if (!authors.contains(user))   // TODO: notify message creator?
            {
                List<Integer> memberList = ann.getMemberListIds();
                if (!memberList.contains(user.getUserId()))
                    return false;
            }
        }
        else
        {
            // Shouldn't be here if preference is NONE
            assert EmailOption.MESSAGES_NONE.getValue() != emailPreference;
        }

        Permissions perm = AnnouncementsController.getPermissions(_c, user, settings);

        return perm.allowRead(ann);
    }


    public Set<User> getNotificationUsers(@Nullable AnnouncementModel ann)
    {
        Collection<User> candidates = getNotificationCandidates();
        Set<User> sendUsers = new HashSet<>(candidates.size());

        for (User user : candidates)
            if (shouldSend(ann, user))
                sendUsers.add(user);

        return sendUsers;
    }


    private static class PreferencePicker
    {
        private final Container _c;
        // srcIdentifier -> UserPreference map for all of this user's preferences
        private final Map<String, UserPreference> _preferenceMap = new HashMap<>();

        private PreferencePicker(Container c)
        {
            _c = c;
        }

        private void addPreference(UserPreference up)
        {
             _preferenceMap.put(up.getSrcIdentifier(), up);
        }

        UserPreference getApplicablePreference(@Nullable AnnouncementModel ann)
        {
            if (null != ann)
            {
                String srcIdentifier = ann.lookupSrcIdentifer();

                // srcIdentfier preference takes precedence, so return it if present
                UserPreference up = _preferenceMap.get(srcIdentifier);

                if (null != up)
                    return up;
            }

            // Return container preference for users who don't have a direct subscription
            UserPreference up = _preferenceMap.get(_c.getId());
            assert null != up;
            return up;
        }

        @Override
        public String toString()
        {
            return "PreferencePicker: " + _c.getId() + " " + _preferenceMap.toString();
        }
    }
}
