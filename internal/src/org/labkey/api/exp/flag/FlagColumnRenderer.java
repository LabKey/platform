/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

package org.labkey.api.exp.flag;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.AliasManager;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.UniqueID;

import java.io.IOException;
import java.io.Writer;

public class FlagColumnRenderer extends DataColumn
{
    protected String defaultTitle = "Flag for review"; // if there is no comment
    protected String endpoint = null;
    // to enable multi edit, the dataregion needs to provide a mapping pk->lsid
    // e.g.  "function(pk) {return pk;}"
    protected String jsConvertPKToLSID = null;

    public FlagColumnRenderer(ColumnInfo colinfo)
    {
        super(colinfo);

        ColumnInfo displayField = colinfo.getDisplayField();
        if (displayField != null)
        {
            setInputType(displayField.getInputType());
        }
        setWidth(null);
    }


    String setFlagFn = null;
    String unique = null;

    protected String getUnique(RenderContext ctx)
    {
        if (null==unique)
            unique = String.valueOf(UniqueID.getRequestScopedUID(ctx.getRequest()));
        return unique;
    }

    protected String renderFlagScript(RenderContext ctx, Writer out)
    {
        if (null != setFlagFn)
            return setFlagFn;

        String dataRegionName = null == ctx.getCurrentRegion() ? null : ctx.getCurrentRegion().getName();
        String dr = dataRegionName == null ? "" : AliasManager.makeLegalName(dataRegionName,null).replace("_","");

        setFlagFn = "__setFlag" + dr + "_" + getUnique(ctx);

        try
        {
            out.write("<script type=\"text/javascript\">\n");
            out.write("var " + setFlagFn + ";");
            out.write("LABKEY.requiresScript('internal/flagColumn', function() {");
            out.write(setFlagFn + " = LABKEY.internal.FlagColumn._showDialog({");
            if (null != endpoint)
                out.write("url: " + PageFlowUtil.jsString(endpoint) + ", ");
            if (null != jsConvertPKToLSID)
                out.write("  translatePrimaryKey : " + jsConvertPKToLSID + ", ");
            out.write("  dataRegionName: " + PageFlowUtil.jsString(dataRegionName) + ", ");
            out.write("  flagEnabledCls: " + PageFlowUtil.jsString(flagEnabledCls()) + ", ");
            out.write("  flagDisabledCls: " + PageFlowUtil.jsString(flagDisabledCls()) + ", ");
            out.write("  imgTitle: " + PageFlowUtil.jsString(defaultTitle));
            out.write("});\n});");
            out.write("</script>");

            return setFlagFn;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }


    protected void renderFlag(RenderContext ctx, Writer out) throws IOException
    {
        renderFlagScript(ctx, out);
        Object boundValue = getColumnInfo().getValue(ctx);
        if (boundValue == null)
            return;

        if (getDisplayColumn() instanceof FlagColumn)
        {
            FlagColumn flagCol = (FlagColumn) getDisplayColumn();
            String comment = (String) flagCol.getValue(ctx);
            String objectId = (String) getValue(ctx);
            if (objectId == null)
                return;
            _renderFlag(ctx, out, objectId, comment);
        }
    }


    Boolean canUpdate = null;

    protected void _renderFlag(RenderContext ctx, Writer out, String objectId, String comment) throws IOException
    {
        setFlagFn = renderFlagScript(ctx, out);

        if (null == canUpdate)
            canUpdate = ctx.getViewContext().hasPermission(UpdatePermission.class);
        if (Boolean.TRUE == canUpdate && null != objectId)
        {
            out.write("<a href=\"#\" onclick=\"return " + setFlagFn + "(");
            out.write(hq(objectId));
            out.write(")\" style=\"color: #aaaaaa\">");
        }

        out.write("<i class=\"" + (null==comment ? flagDisabledCls() : flagEnabledCls()) + "\"");
        if (comment == null && Boolean.TRUE == canUpdate && null != objectId)
            comment = defaultTitle;
        out.write(" title=\"" + h(comment) + "\"");
        out.write(" flagId=\"" + h(objectId) + "\"");
        out.write("/>");

        if (canUpdate)
        {
            out.write("</a>");
        }
    }

    public static String flagEnabledCls()
    {
        return "fa fa-flag lk-flag-enabled";
    }

    public static String flagDisabledCls()
    {
        return "fa fa-flag-o lk-flag-disabled";
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderFlag(ctx, out);
    }

    @Override
    protected Object getInputValue(RenderContext ctx)
    {
        FlagColumn displayField = (FlagColumn) getColumnInfo().getDisplayField();

        if(null != displayField)
            return displayField.getValue(ctx);

        return displayField;
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        // never return null
        return StringUtils.trimToEmpty((String)super.getDisplayValue(ctx));
    }
}
