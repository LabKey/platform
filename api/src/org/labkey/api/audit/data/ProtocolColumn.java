/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.audit.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;

/**
 * User: klum
 * Date: Mar 15, 2012
 */
public class ProtocolColumn extends ExperimentAuditColumn
{
    public ProtocolColumn(ColumnInfo col, ColumnInfo containerId, @Nullable ColumnInfo defaultName)
    {
        super(col, containerId, defaultName);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object protocolId = getBoundColumn().getValue(ctx);
        String cId = (String)ctx.get("ContainerId");
        if (cId == null)
            cId = (String)ctx.get("Container");

        if (protocolId != null && cId != null)
        {
            Container c = ContainerManager.getForId(cId);
            if (c != null)
            {
                ExpProtocol protocol;

                if (protocolId instanceof Integer)
                    protocol = ExperimentService.get().getExpProtocol((Integer)protocolId);
                else
                    protocol = ExperimentService.get().getExpProtocol(protocolId.toString());

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
            Pair<String, String> key3 = splitKey3(_defaultName.getValue(ctx));
            out.write(key3 != null ? PageFlowUtil.filter(key3.getKey()) : "&nbsp;");
        }
        else
            out.write("&nbsp;");
    }
}
