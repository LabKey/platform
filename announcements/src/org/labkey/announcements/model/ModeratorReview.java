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
        public boolean isApproved(Container c, User user)
        {
            return true;
        }
    },
    InitialPost
    {
        @Override
        public boolean isApproved(Container c, User user)
        {
            // Users approved by All setting are automatically approved here
            if (All.isApproved(c, user))
                return true;

            // Does this user have at least one approved announcement in this message board?
            Filter filter = SimpleFilter.createContainerFilter(c)
                .addCondition(FieldKey.fromParts("CreatedBy"), user)
                .addAllClauses(AnnouncementManager.IS_APPROVED_FILTER);

            return new TableSelector(CommSchema.getInstance().getTableInfoAnnouncements(), filter, null).exists();
        }
    },
    All
    {
        @Override
        public boolean isApproved(Container c, User user)
        {
            // Editors and above don't require moderator review; check for Delete permission as a proxy
            return c.hasPermission(user, DeletePermission.class);
        }
    };

    public abstract boolean isApproved(Container c, User user);

    public static ModeratorReview get(String s)
    {
        ModeratorReview mr = EnumUtils.getEnum(ModeratorReview.class, s);

        return null != mr ? mr : None;
    }
}
