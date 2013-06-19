/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Oct 16, 2007
 *
 * Event field documentation:
 *
 * created - Timestamp
 * createdBy - User who created the record
 * impersonatedBy - user who was impersonating the user (or null)
 * comment - record description
 * projectId - the project id
 * container - container id of the domain event
 * entityId - entity id of the attachment parent
 * key1 - the attachment name
 *
 */
public class AttachmentAuditViewFactory extends SimpleAuditViewFactory
{
    private static final AttachmentAuditViewFactory _instance = new AttachmentAuditViewFactory();

    private AttachmentAuditViewFactory(){}

    public static AttachmentAuditViewFactory getInstance()
    {
        return _instance;
    }

    public String getEventType()
    {
        return AttachmentService.ATTACHMENT_AUDIT_EVENT;
    }

    public String getName()
    {
        return "Attachment events";
    }

    @Override
    public String getDescription()
    {
        return "Displays information about attachment events.";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("ContainerId"));
        columns.add(FieldKey.fromParts("Key1"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void setupTable(FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);
        ColumnInfo col = table.getColumn("Key1");
        if (col != null)
        {
            col.setLabel("Attachment");
            col.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        public String getName()
                        {
                            return "attachment";
                        }
                    };
                }
            });
        }
    }

    public static AuditLogQueryView createAttachmentView(ViewContext context, AttachmentParent parent)
    {
        SimpleFilter filter = new SimpleFilter("ContainerId", parent.getContainerId());
        filter.addCondition("EntityId", parent.getEntityId());

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, AttachmentService.ATTACHMENT_AUDIT_EVENT);
        view.setTitle("Attachments History:");
        view.setSort(new Sort("-Date"));
        view.setVisibleColumns(new String[]{"Date", "CreatedBy", "Comment"});

        return view;
    }
}
