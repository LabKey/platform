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

package org.labkey.experiment;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.data.ExperimentAuditColumn;
import org.labkey.api.audit.data.ProtocolColumn;
import org.labkey.api.audit.data.RunColumn;
import org.labkey.api.audit.data.RunGroupColumn;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Britt Piehler
 * Date: October 12, 2009
 *
 * Event field documentation:
 *
 * created - Timestamp
 * createdBy - User who created the record
 * impersonatedBy - user who was impersonating the user (or null)
 * comment - record description
 * projectId - the project id
 * container - container id of the domain event
 * key1 - the protocol LSID
 * key2 - the run LSID
 * key3 - the protocol name
 */
public class ExperimentAuditViewFactory extends SimpleAuditViewFactory
{
    private static final ExperimentAuditViewFactory _instance = new ExperimentAuditViewFactory();
    public static final String EXPERIMENT_AUDIT_EVENT = "ExperimentAuditEvent";

    private ExperimentAuditViewFactory(){}

    public static ExperimentAuditViewFactory getInstance()
    {
        return _instance;
    }

    public String getEventType()
    {
        return EXPERIMENT_AUDIT_EVENT;
    }

    public String getName()
    {
        return "Assay/Experiment events";
    }

    @Override
    public String getDescription()
    {
        return "Describes information about assay run events.";
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
        columns.add(FieldKey.fromParts("Key1"));
        columns.add(FieldKey.fromParts("Key2"));
        columns.add(FieldKey.fromParts("IntKey1"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public static String getKey3(ExpProtocol protocol, ExpRun run)
    {
        return protocol.getName() + ExperimentAuditColumn.KEY_SEPARATOR + (run != null ? run.getName() : "");
    }

    public void setupTable(final FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);
        final ColumnInfo containerId = table.getColumn("ContainerId");

        ColumnInfo protocolCol = table.getColumn("Key1");
        protocolCol.setLabel("Assay/Protocol");
        protocolCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ProtocolColumn(colInfo, containerId, table.getColumn("Key3"));
            }
        });


        ColumnInfo runCol = table.getColumn("Key2");
        runCol.setLabel("Run");
        runCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new RunColumn(colInfo, containerId, table.getColumn("Key3"));
            }
        });

        ColumnInfo runGroupCol = table.getColumn("IntKey1");
        runGroupCol.setLabel("Run Group");
        runGroupCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new RunGroupColumn(colInfo, containerId, table.getColumn("Key3"));
            }
        });
    }
}
