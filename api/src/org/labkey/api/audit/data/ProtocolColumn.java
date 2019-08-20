/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
public class ProtocolColumn extends ExperimentAuditColumn<ExpProtocol>
{
    public ProtocolColumn(ColumnInfo col, ColumnInfo containerId, @Nullable ColumnInfo defaultName)
    {
        super(col, containerId, defaultName);
    }

    @Nullable
    @Override
    protected Pair<ExpProtocol, ActionURL> getExpValue(RenderContext ctx)
    {
        Object protocolId = getBoundColumn().getValue(ctx);

        if (protocolId != null)
        {
            Container c = getContainer(ctx);
            if (c != null)
            {
                ExpProtocol protocol;

                if (protocolId instanceof Integer)
                    protocol = ExperimentService.get().getExpProtocol((Integer) protocolId);
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
                return protocol == null ? null : new Pair<>(protocol, url);
            }
        }
        return null;
    }

    @Override
    protected String extractFromKey3(RenderContext ctx)
    {
        Object value = _defaultName.getValue(ctx);
        if (value == null)
            return null;
        String[] parts = value.toString().split(KEY_SEPARATOR);
        if (parts.length != 2)
            return null;
        return parts[0];
    }

}
