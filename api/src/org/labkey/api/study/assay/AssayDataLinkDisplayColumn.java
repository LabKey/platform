/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpProtocolTable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.FieldKey;

import java.io.Writer;
import java.io.IOException;
import java.util.*;

/**
 * User: jeckels
 * Date: Jul 23, 2007
 */
public class AssayDataLinkDisplayColumn extends DataColumn
{
    private ColumnInfo _protocolColumnInfo;
    private Map<Number, ExpProtocol> _protocols = new HashMap<>();
    private ColumnInfo _runIdColumnInfo;
    private ContainerFilter _containerFilter;
    private ColumnInfo _containerColumnInfo;

    public AssayDataLinkDisplayColumn(ColumnInfo colInfo, ContainerFilter containerFilter)
    {
        super(colInfo);
        _containerFilter = containerFilter;
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        // RunId and ProtocolId may not be available if we are in a custom query
        Number runId = _runIdColumnInfo == null ? null : (Number)_runIdColumnInfo.getValue(ctx);
        Number protocolId = _protocolColumnInfo == null ? null : (Number)_protocolColumnInfo.getValue(ctx);
        String containerId = (null != _containerColumnInfo) ? (String)_containerColumnInfo.getValue(ctx) : null;
        if (runId != null && protocolId != null && containerId != null)
        {
            ExpProtocol protocol = _protocols.get(protocolId);
            if (protocol == null)
            {
                protocol = ExperimentService.get().getExpProtocol(protocolId.intValue());
                _protocols.put(protocolId, protocol);
            }
            Container container = ContainerManager.getContainerService().getForId(containerId);
            if (null != container)
            {
                ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(container, protocol, _containerFilter, runId.intValue());

                out.write("<a href=\"" + url.getLocalURIString() + "\" title=\"View the data for just this run\">" +
                        PageFlowUtil.filter(getDisplayColumn().getValue(ctx)) + "</a>");
            }
            else
            {
                out.write(PageFlowUtil.filter(getDisplayColumn().getValue(ctx)));
            }
        }
        else
        {
            out.write(PageFlowUtil.filter(getDisplayColumn().getValue(ctx)));
        }
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        if (_protocolColumnInfo == null)
        {
            FieldKey thisColKey = FieldKey.fromString(getColumnInfo().getName());
            FieldKey parentColKey = thisColKey.getParent();
            FieldKey protocolIdKey = new FieldKey(new FieldKey(parentColKey, ExpRunTable.Column.Protocol.name()), ExpProtocolTable.Column.RowId.name());
            FieldKey runIdKey = new FieldKey(parentColKey, ExpRunTable.Column.RowId.name());
            FieldKey containerColKey = new FieldKey(parentColKey, ExpRunTable.Column.Folder.name());
            Map<FieldKey,ColumnInfo> extraCols = QueryService.get().getColumns(getColumnInfo().getParentTable(), Arrays.asList(protocolIdKey, runIdKey, containerColKey));
            _protocolColumnInfo = extraCols.get(protocolIdKey);
            _runIdColumnInfo = extraCols.get(runIdKey);
            _containerColumnInfo = extraCols.get(containerColKey);
        }
        // RunId and ProtocolId may not be available if we are in a custom query
        if (_protocolColumnInfo != null)
        {
            columns.add(_protocolColumnInfo);
        }
        if (_runIdColumnInfo != null)
        {
            columns.add(_runIdColumnInfo);
        }
        if (null != _containerColumnInfo)
        {
            columns.add(_containerColumnInfo);
        }
    }
}
