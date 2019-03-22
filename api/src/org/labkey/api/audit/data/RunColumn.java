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
import org.labkey.api.exp.api.ExpRun;
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
public class RunColumn extends ExperimentAuditColumn<ExpRun>
{
    public RunColumn(ColumnInfo col, ColumnInfo containerId, @Nullable ColumnInfo defaultName)
    {
        super(col, containerId, defaultName);
    }

    protected String extractFromKey3(RenderContext ctx)
    {
        Object value = _defaultName.getValue(ctx);
        if (value == null)
            return null;
        String[] parts = value.toString().split(KEY_SEPARATOR);
        if (parts.length != 2)
            return null;
        return parts[1].length() > 0 ? parts[1] : null;
    }

    @Override
    @Nullable
    protected Pair<ExpRun, ActionURL> getExpValue(RenderContext ctx)
    {
        String runLsid = (String) getBoundColumn().getValue(ctx);
        if (runLsid != null)
        {
            Container c = getContainer(ctx);
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

                return run == null ? null : new Pair<>(run, url);
            }
        }
        return null;
    }
}
