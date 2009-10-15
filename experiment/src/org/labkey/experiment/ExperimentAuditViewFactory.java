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

package org.labkey.experiment;

import org.apache.commons.lang.ObjectUtils;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayUrls;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    public QueryView createDefaultQueryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("EventType", EXPERIMENT_AUDIT_EVENT);

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
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("Key1"));
        columns.add(FieldKey.fromParts("Key2"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    private static final String KEY_SEPARATOR = "~~KEYSEP~~";

    public static String getKey3(ExpProtocol protocol, ExpRun run)
    {
        return protocol.getName() + KEY_SEPARATOR + (run != null ? run.getName() : "");
    }

    protected static Pair<String, String> splitKey3(String value)
    {
        if (value == null)
            return null;
        String[] parts = value.split(KEY_SEPARATOR);
        if (parts == null || parts.length != 2)
            return null;
        return new Pair<String, String>(parts[0], parts[1].length() > 0 ? parts[1] : null);
    }

    public void setupTable(final TableInfo table)
    {
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
    }

    private static class ExperimentAuditColumn extends DataColumn
    {
        protected ColumnInfo _containerId;
        protected ColumnInfo _defaultName;

        public ExperimentAuditColumn(ColumnInfo col, ColumnInfo containerId, ColumnInfo defaultName)
        {
            super(col);
            _containerId = containerId;
            _defaultName = defaultName;
        }

        public String getName()
        {
            return getColumnInfo().getLabel();
        }

        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            super.addQueryColumns(columns);
            if (_containerId != null)
                columns.add(_containerId);
            if (_defaultName != null)
                columns.add(_defaultName);
        }

        public boolean isFilterable()
        {
            return false;
        }
    }

    private static class ProtocolColumn extends ExperimentAuditColumn
    {

        public ProtocolColumn(ColumnInfo col, ColumnInfo containerId, ColumnInfo defaultName)
        {
            super(col, containerId, defaultName);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String protocolLsid = (String)getBoundColumn().getValue(ctx);
            String cId = (String)ctx.get("ContainerId");
            if (protocolLsid != null && cId != null)
            {
                Container c = ContainerManager.getForId(cId);
                if (c != null)
                {
                    ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolLsid);
                    AssayProvider provider = null;
                    if (protocol != null)
                        provider = AssayService.get().getProvider(protocol);

                    ActionURL url = null;
                    if (provider != null)
                        url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(c, protocol);
                    else if (protocol != null)
                        url = PageFlowUtil.urlProvider(ExperimentUrls.class).getProtocolDetailsURL(protocol);

                    if (url != null)
                    {
                        out.write("<a href=\"" + url.getLocalURIString() + "\">" + PageFlowUtil.filter(protocol.getName()) + "</a>");
                        return;
                    }
                }
            }

            if (_defaultName != null)
            {
                Pair<String, String> key3 = splitKey3(_defaultName.getValue(ctx).toString());
                out.write(key3 != null ? PageFlowUtil.filter(key3.getKey()) : "&nbsp;");
            }
            else
                out.write("&nbsp;");
        }

    }

    private static class RunColumn extends ExperimentAuditColumn
    {
        public RunColumn(ColumnInfo col, ColumnInfo containerId, ColumnInfo defaultName)
        {
            super(col, containerId, defaultName);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String runLsid = (String)getBoundColumn().getValue(ctx);
            String cId = (String)ctx.get("ContainerId");
            if (runLsid != null && cId != null)
            {
                Container c = ContainerManager.getForId(cId);
                if (c != null)
                {
                    ExpRun run = ExperimentService.get().getExpRun(runLsid);
                    ExpProtocol protocol = null;
                    if (run != null)
                        protocol = run.getProtocol();
                    AssayProvider provider = null;
                    if (protocol != null)
                        provider = AssayService.get().getProvider(protocol);

                    ActionURL url = null;
                    if (provider != null)
                        url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(c, protocol, run.getRowId());
                    else if (run != null)
                        url = PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphURL(run);

                    if (url != null)
                    {
                        out.write("<a href=\"" + url.getLocalURIString() + "\">" + PageFlowUtil.filter(run.getName()) + "</a>");
                        return;
                    }
                }
            }
            Pair<String, String> key3 = splitKey3(_defaultName.getValue(ctx).toString());
            out.write(key3 != null && key3.getValue() != null ? PageFlowUtil.filter(key3.getValue()) : "&nbsp;");
        }
    }
}