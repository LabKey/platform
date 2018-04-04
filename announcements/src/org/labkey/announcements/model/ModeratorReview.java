package org.labkey.announcements.model;

import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.EnumUtils;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;

// Keep these in-sync with DiscussionService
public enum ModeratorReview
{
    None
    {
        @Override
        boolean isApproved(Container c, User user, AnnouncementModel ann)
        {
            return true;
        }
    },
    InitialPost
    {
        @Override
        boolean isApproved(Container c, User user, AnnouncementModel ann)
        {
            // Users automatically approved by All setting are also approved here
            if (All.isApproved(c, user, ann))
                return true;

            // Does this user have at least one approved announcement in this message board?
            int userId = ann.getCreatedBy();

            Filter filter = SimpleFilter.createContainerFilter(c)
                .addCondition(FieldKey.fromParts("CreatedBy"), userId)
                .addCondition(FieldKey.fromParts("Approved"), AnnouncementManager.SPAM_MAGIC_DATE, CompareType.GT);

            return new TableSelector(CommSchema.getInstance().getTableInfoAnnouncements(), filter, null).exists();
        }
    },
    All
    {
        @Override
        boolean isApproved(Container c, User user, AnnouncementModel ann)
        {
            // Editors and above don't require moderator review; check for Delete permission as a proxy
            return c.hasPermission(user, DeletePermission.class);
        }
    };

    abstract boolean isApproved(Container c, User user, AnnouncementModel ann);

    static ModeratorReview get(String s)
    {
        ModeratorReview mr = EnumUtils.getEnum(ModeratorReview.class, s);

        return null != mr ? mr : None;
    }
}
