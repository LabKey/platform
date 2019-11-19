/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
package org.labkey.announcements.query;

import org.labkey.announcements.AnnouncementModule;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: Nick
 * Date: Jul 1, 2010
 * Time: 4:04:11 PM
 */
public class AnnouncementSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "announcement";
    public static final String ANNOUNCEMENT_TABLE_NAME = "Announcement";
    public static final String MODERATOR_REVIEW_TABLE_NAME = "ModeratorReview";
    public static final String SPAM_TABLE_NAME = "Spam";
    public static final String FORUM_SUBSCRIPTION_TABLE_NAME = "ForumSubscription";
    public static final String ANNOUNCEMENT_SUBSCRIPTION_TABLE_NAME = "AnnouncementSubscription";
    public static final String EMAIL_OPTION_TABLE_NAME = "EmailOption";
    public static final String EMAIL_FORMAT_TABLE_NAME = "EmailFormat";
    public static final String RSS_FEEDS_TABLE_NAME = "RSSFeeds";
    public static final String TOURS_TABLE_NAME = "Tours";
    public static final String THREADS_TABLE_NAME = "Threads";

    public static final int PAGE_TYPE_ID = 0;

    private static final Set<String> TABLE_NAMES;

    static
    {
        Set<String> names = new TreeSet<>();
        names.add(ANNOUNCEMENT_TABLE_NAME);
        names.add(FORUM_SUBSCRIPTION_TABLE_NAME);
        names.add(ANNOUNCEMENT_SUBSCRIPTION_TABLE_NAME);
        names.add(EMAIL_OPTION_TABLE_NAME);
        names.add(EMAIL_FORMAT_TABLE_NAME);
        names.add(RSS_FEEDS_TABLE_NAME);
        names.add(TOURS_TABLE_NAME);
        names.add(THREADS_TABLE_NAME);
        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    public static void register(Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                // Schema is always available unless marked as secure.
                // Brute force fix for #3453 -- no query access to secure message board  TODO: Filter based on permissions instead.
                return !AnnouncementsController.getSettings(schema.getContainer()).isSecure();
            }

            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new AnnouncementSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public AnnouncementSchema(User user, Container container)
    {
        super(SCHEMA_NAME, "Contains forums, announcements, and subscriptions", user, container, CommSchema.getInstance().getSchema());
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (ANNOUNCEMENT_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createAnnouncementTable(cf);
        }
        if (MODERATOR_REVIEW_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createModeratorReviewTable(cf);
        }
        if (SPAM_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createSpamTable(cf);
        }
        if (EMAIL_FORMAT_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createEmailFormatTable(cf);
        }
        if (EMAIL_OPTION_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createEmailOptionTable(cf);
        }
        if (FORUM_SUBSCRIPTION_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createForumSubscriptionTable(cf);
        }
        if (ANNOUNCEMENT_SUBSCRIPTION_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createAnnouncementSubscriptionTable(cf);
        }
        if (RSS_FEEDS_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createRSSFeedsTable(cf);
        }
        if (TOURS_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createToursTable(cf);
        }
        if (THREADS_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createThreadsTable(cf);
        }
        return null;
    }

    public TableInfo createEmailFormatTable(ContainerFilter cf)
    {
        FilteredTable result = new FilteredTable<>(CommSchema.getInstance().getTableInfoEmailFormats(), this, cf);
        result.setName(EMAIL_FORMAT_TABLE_NAME);
        result.wrapAllColumns(true);
        result.setPublicSchemaName(getName());
        return result;
    }

    public TableInfo createEmailOptionTable(ContainerFilter cf)
    {
        FilteredTable result = new FilteredTable<>(CommSchema.getInstance().getTableInfoEmailOptions(), this, cf);
        result.setName(EMAIL_OPTION_TABLE_NAME);
        result.addWrapColumn(result.getRealTable().getColumn("EmailOptionId"));
        result.addWrapColumn(result.getRealTable().getColumn("EmailOption"));
        result.setPublicSchemaName(getName());
        result.addCondition(result.getRealTable().getColumn("Type"), "messages");
        return result;
    }

    private TableInfo createRSSFeedsTable(ContainerFilter cf)
    {
        return new RSSFeedsTable(this, cf);
    }

    private TableInfo createAnnouncementSubscriptionTable(ContainerFilter cf)
    {
        return new AnnouncementSubscriptionTable(this, cf);
    }

    private TableInfo createForumSubscriptionTable(ContainerFilter cf)
    {
        return new ForumSubscriptionTable(this, cf);
    }

    public AnnouncementTable createAnnouncementTable(ContainerFilter cf)
    {
        return new AnnouncementTable(this, cf);
    }

    private AnnouncementTable createFilteredAnnouncementTable(ContainerFilter cf, SimpleFilter filter)
    {
        AnnouncementTable table = new AnnouncementTable(this, cf, filter);

        for (String name : Arrays.asList("Expires", "RendererType", "Status", "AssignedTo", "DiscussionSrcIdentifier", "DiscussionSrcURL", "Folder", "LastIndexed"))
            table.getMutableColumn(name).setHidden(true);

        return table;
    }

    private AnnouncementTable createModeratorReviewTable(ContainerFilter cf)
    {
        return createFilteredAnnouncementTable(cf, AnnouncementManager.REQUIRES_REVIEW_FILTER);
    }

    private AnnouncementTable createSpamTable(ContainerFilter cf)
    {
        AnnouncementTable spamTable = createFilteredAnnouncementTable(cf, AnnouncementManager.IS_SPAM_FILTER);
        spamTable.setTitle("Spam");

        return spamTable;
    }

    private TableInfo createToursTable(ContainerFilter cf)
    {
        return new ToursTable(this, cf);
    }

    public TableInfo createThreadsTable(ContainerFilter cf)
    {
        return new ThreadsTable(this, cf);
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }

    @Override
    public boolean isHidden()
    {
        Module module = ModuleLoader.getInstance().getModule(AnnouncementModule.NAME);
        return !getContainer().getActiveModules().contains(module);
    }
}
