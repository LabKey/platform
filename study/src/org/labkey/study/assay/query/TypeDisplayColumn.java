/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * User: jeckels
 * Date: Oct 17, 2007
 */
public class TypeDisplayColumn extends DataColumn
{
    private static final Logger LOG = Logger.getLogger(TypeDisplayColumn.class);

    private static final FieldKey LSID_FIELD_KEY = new FieldKey(null, "LSID");

    public TypeDisplayColumn(ColumnInfo colInfo)
    {
        super(colInfo);
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.add(LSID_FIELD_KEY);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String providerName = (String)getColumnInfo().getValue(ctx);
        if (providerName != null)
        {
            // We successfully matched an AssayProvider so render normally.
            super.renderGridCellContents(ctx, out);
            return;
        }
        else
        {
            // Fallback to using LSID
            String lsid = (String) ctx.get(LSID_FIELD_KEY);
            if (lsid != null)
            {
                ExpProtocol protocol = ExperimentService.get().getExpProtocol(lsid);
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider != null)
                {
                    LOG.warn("Failed to match AssayProvider '" + provider.getName() + "' using pattern '" + provider.getProtocolPattern() + "' for LSID: " + lsid);
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
        }

        out.write(PageFlowUtil.filter("<AssayProvider Not Found>"));
    }
}
