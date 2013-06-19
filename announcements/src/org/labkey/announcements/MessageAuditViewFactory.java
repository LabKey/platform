/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.announcements;

import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.MailHelper;
import org.labkey.api.data.*;

import java.util.List;
import java.util.ArrayList;

/**
 * User: klum
 * Date: Aug 18, 2009
 * Event field documentation:
 *
 * created - Timestamp
 * createdBy - User who created the record
 * comment - record description
 * key1 - the address(es) of the user(s) sending the message
 * key2 - the address(es) of the user(s) receiving the message
 * key3 - the content type of the message
 */
public class MessageAuditViewFactory extends SimpleAuditViewFactory
{
    private static final MessageAuditViewFactory _instance = new MessageAuditViewFactory();

    public static MessageAuditViewFactory getInstance()
    {
        return _instance;
    }
    
    private MessageAuditViewFactory(){}

    public String getEventType()
    {
        return MailHelper.MESSAGE_AUDIT_EVENT;
    }

    public String getName()
    {
        return "Message events";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("Key1"));
        columns.add(FieldKey.fromParts("Key2"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void setupTable(FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);
        ColumnInfo col1 = table.getColumn("Key1");
        if (col1 != null)
        {
            col1.setLabel("From");
            col1.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        public String getName() {return "from";}
                    };
                }
            });
        }

        ColumnInfo col2 = table.getColumn("Key2");
        if (col2 != null)
        {
            col2.setLabel("To");
            col2.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        public String getName() {return "to";}
                    };
                }
            });
        }

        ColumnInfo col3 = table.getColumn("Key3");
        if (col3 != null)
        {
            col3.setLabel("Content Type");
            col3.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        public String getName() {return "contentType";}
                    };
                }
            });
        }
    }
}
