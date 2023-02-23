/*
 * Copyright (c) 2018 LabKey Corporation
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

import org.apache.commons.lang3.EnumUtils;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;

// Keep these in-sync with DiscussionService
public enum ModeratorReview
{
    None
    {
        @Override
        public boolean isApproved(Container c, User user, boolean newThread)
        {
            return true;
        }
    },
    InitialPost
    {
        @Override
        public boolean isApproved(Container c, User user, boolean newThread)
        {
            // Users approved by All setting are automatically approved here
            if (All.isApproved(c, user, newThread))
                return true;

            // Does this user have at least one approved announcement in this message board?
            Filter filter = SimpleFilter.createContainerFilter(c)
                .addCondition(FieldKey.fromParts("CreatedBy"), user)
                .addAllClauses(AnnouncementManager.IS_APPROVED_FILTER);

            return new TableSelector(CommSchema.getInstance().getTableInfoAnnouncements(), filter, null).exists();
        }
    },
    NewThread
    {
        @Override
        public boolean isApproved(Container c, User user, boolean newThread)
        {
            if (All.isApproved(c, user, newThread))
                return true;

            // Approve if this is not a new thread
            return !newThread;
        }
    },
    All
    {
        @Override
        public boolean isApproved(Container c, User user, boolean newThread)
        {
            // Editors and above don't require moderator review; check for Delete permission as a proxy
            return c.hasPermission(user, DeletePermission.class);
        }
    };

    public abstract boolean isApproved(Container c, User user, boolean newThread);

    public static ModeratorReview get(String s)
    {
        ModeratorReview mr = EnumUtils.getEnum(ModeratorReview.class, s);

        return null != mr ? mr : None;
    }

    public boolean sameAs(String reviewType)
    {
        return name().equals(reviewType);
    }

    public static boolean requiresReview(String reviewType)
    {
        return InitialPost.sameAs(reviewType) || NewThread.sameAs(reviewType) || All.sameAs(reviewType);
    }
}
