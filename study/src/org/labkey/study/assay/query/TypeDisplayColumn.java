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

package org.labkey.study.assay.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;

/**
 * User: jeckels
 * Date: Oct 17, 2007
 */
public class TypeDisplayColumn extends DataColumn
{
    public TypeDisplayColumn(ColumnInfo colInfo)
    {
        super(colInfo);
    }

    public boolean isFilterable()
    {
        return false;
    }

    public boolean isSortable()
    {
        return false;
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String lsid = (String)getColumnInfo().getValue(ctx);
        if (lsid != null)
        {
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(lsid);
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider != null)
            {
                out.write(PageFlowUtil.filter(provider.getName()));
                return;
            }
            else if (protocol != null)
            {
                out.write(PageFlowUtil.filter("<Unknown>"));
                // We won't be showing our normal UI that lets an admin delete the design, so let the user do it directly
                // from here
                if (protocol.getContainer().getPolicy().hasPermissions(ctx.getViewContext().getUser(), DesignAssayPermission.class, DeletePermission.class))
                {
                    out.write(" ");
                    out.write(PageFlowUtil.textLink("Delete Assay Design", PageFlowUtil.urlProvider(ExperimentUrls.class).getDeleteProtocolURL(protocol, PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(ctx.getContainer()))));
                }
                return;
            }
        }

        out.write(PageFlowUtil.filter("<AssayProvider Not Found>"));
    }
}
