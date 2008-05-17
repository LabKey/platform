/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.api.*;
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
    private Map<Number, ExpProtocol> _protocols = new HashMap<Number, ExpProtocol>();
    private ColumnInfo _runIdColumnInfo;

    public AssayDataLinkDisplayColumn(ColumnInfo colInfo)
    {
        super(colInfo);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Number runId = (Number)_runIdColumnInfo.getValue(ctx);
        Number protocolId = (Number)_protocolColumnInfo.getValue(ctx);
        if (runId != null && protocolId != null)
        {
            ExpProtocol protocol = _protocols.get(protocolId);
            if (protocol == null)
            {
                protocol = ExperimentService.get().getExpProtocol(protocolId.intValue());
                _protocols.put(protocolId, protocol);
            }
            ActionURL url = AssayService.get().getAssayDataURL(ctx.getContainer(), protocol, runId.intValue());
            out.write("<a href=\"" + url.getLocalURIString() + "\" title=\"View the data for just this run\">" +
                    PageFlowUtil.filter(ctx.get(getDisplayColumn().getName())) + "</a>");
        }
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        if (_protocolColumnInfo == null)
        {
            FieldKey protocolIdKey = FieldKey.fromParts(ExpRunTable.Column.Protocol.name(), ExpProtocolTable.Column.RowId.name());
            FieldKey runIdKey = FieldKey.fromParts(ExpRunTable.Column.RowId.name());
            Map<FieldKey,ColumnInfo> extraCols = QueryService.get().getColumns(getColumnInfo().getParentTable(), Arrays.asList(protocolIdKey, runIdKey));
            _protocolColumnInfo = extraCols.get(protocolIdKey);
            _runIdColumnInfo = extraCols.get(runIdKey);
            assert _protocolColumnInfo != null : "Could not find protocol rowId column";
            assert _runIdColumnInfo != null : "Could not find run rowId column";
        }
        columns.add(_protocolColumnInfo);
    }
}
