/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.webdav;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: 8/28/13
 *
 * Legacy audit view factory, needed so file system batch events that were previously logged through the
 * file system view factory can be migrated cleanly to the new audit architecture.
 */
public class FileSystemBatchAuditViewFactory extends SimpleAuditViewFactory
{
    public static final String BATCH_EVENT_TYPE = "FileSystemBatch";
    private static final FileSystemBatchAuditViewFactory _instance = new FileSystemBatchAuditViewFactory();

    private FileSystemBatchAuditViewFactory(){}

    public static FileSystemBatchAuditViewFactory getInstance()
    {
        return _instance;
    }

    public String getEventType()
    {
        return BATCH_EVENT_TYPE;
    }

    public String getName()
    {
        return "File batch events";
    }

    @Override
    public String getDescription()
    {
        return "Displays information about file uploads and modifications.";
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
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }
}
