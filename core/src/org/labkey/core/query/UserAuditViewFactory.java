/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.core.query;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.security.UserManager;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 16, 2007
 *
 * Event field documentation:
 *
 * created - Timestamp
 * createdBy - User who created the record
 * impersonatedBy - user who was impersonating the user (or null)
 * comment - record description
 * intKey1 - the user id of the principal being modified
 *
 */
public class UserAuditViewFactory extends SimpleAuditViewFactory
{
    private static final UserAuditViewFactory _instance = new UserAuditViewFactory();

    public static UserAuditViewFactory getInstance()
    {
        return _instance;
    }

    private UserAuditViewFactory(){}

    public String getEventType()
    {
        return UserManager.USER_AUDIT_EVENT;
    }

    public String getName()
    {
        return "User events";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        return createUserHistoryView(context, null);
    }

    public AuditLogQueryView createUserHistoryView(ViewContext context, SimpleFilter extraFilter)
    {
        SimpleFilter filter = new SimpleFilter("EventType", UserManager.USER_AUDIT_EVENT);

        if (null != extraFilter)
            filter.addAllClauses(extraFilter);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setSort(new Sort("-Date"));
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("IntKey1"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void setupTable(TableInfo table)
    {
        ColumnInfo col = table.getColumn("IntKey1");
        if (col != null)
        {
            UserIdForeignKey.initColumn(col);
            col.setLabel("User");
            col.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        public String getName()
                        {
                            return "user";
                        }
                    };
                }
            });
        }
    }
}
