/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.query.DefaultAuditSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Oct 5, 2007
 */
/**
 * A no-op implementation of an audit log service
 */
public class DefaultAuditProvider implements AuditLogService, AuditLogService.Replaceable
{
    public boolean isViewable()
    {
        return false;
    }

    @Override
    public <K extends AuditTypeEvent> K addEvent(User user, K event)
    {
        return null;
    }

    @Nullable
    @Override
    public <K extends AuditTypeEvent> K getAuditEvent(User user, String eventType, int rowId)
    {
        return null;
    }

    @Override
    public <K extends AuditTypeEvent> List<K> getAuditEvents(Container container, User user, String eventType, @Nullable SimpleFilter filter, @Nullable Sort sort)
    {
        return Collections.emptyList();
    }

    public String getTableName()
    {
        return "default";
    }

    public TableInfo getTable(ViewContext context, String name)
    {
        return null;
    }

    public UserSchema createSchema(User user, Container container)
    {
        return new DefaultAuditSchema(user, container);
    }

    @Override
    public ActionURL getAuditUrl()
    {
        return null;
    }
}
