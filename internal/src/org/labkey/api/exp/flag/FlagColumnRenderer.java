/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
    protected String flagSrc;
    protected String unflagSrc;
    protected String defaultTitle = "Flag for review"; // if there is no comment
    protected String endpoint = null;
    // to enable multi edit, the dataregion needs to provide a mapping pk->lsid
    // e.g.  "function(pk) {return pk;}"
    protected String jsConvertPKToLSID = null;

    public FlagColumnRenderer(ColumnInfo colinfo)
    {
        this(colinfo, null, null);
    }

    public FlagColumnRenderer(ColumnInfo colinfo, String unflagSrc, String flagSrc)
    {
        super(colinfo);

        ColumnInfo displayField = colinfo.getDisplayField();
        if (displayField != null)
        {
            setInputType(displayField.getInputType());
        }
        String contextPath = AppProps.getInstance().getContextPath();
        this.unflagSrc = null==unflagSrc ? null : contextPath + unflagSrc;
        this.flagSrc = null==flagSrc ? null : contextPath + flagSrc;
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

        if (getDisplayColumn() instanceof FlagColumn)
        {
            FlagColumn flagCol = (FlagColumn)getDisplayColumn();
            if (null == flagSrc)
                flagSrc = flagCol.urlFlag(true);
            if (null == unflagSrc)
                unflagSrc = flagCol.urlFlag(false);
        }
        if (null == flagSrc)
            flagSrc = AppProps.getInstance().getContextPath() +"/Experiment/flagDefault.gif";
        if (null == unflagSrc)
            unflagSrc = AppProps.getInstance().getContextPath() +"/Experiment/unflagDefault.gif";

        try
        {
            out.write("<script type=\"text/javascript\">\n");
            out.write("var " + setFlagFn + ";");
            out.write("LABKEY.requiresExt4Sandbox(function() {");
            out.write("LABKEY.requiresScript('internal/flagColumn.js', function() {");
            out.write(setFlagFn + " = LABKEY.internal.FlagColumn._showDialog({");
            if (null != endpoint)
                out.write("url: " + PageFlowUtil.jsString(endpoint) + ", ");
            if (null != jsConvertPKToLSID)
                out.write("  translatePrimaryKey : " + jsConvertPKToLSID + ", ");
            out.write("  dataRegionName: " + PageFlowUtil.jsString(dataRegionName) + ", ");
            out.write("  imgSrcFlagged: " + PageFlowUtil.jsString(flagSrc) + ", ");
            out.write("  imgSrcUnflagged: " + PageFlowUtil.jsString(unflagSrc) + ", ");
            out.write("  imgTitle: " + PageFlowUtil.jsString(defaultTitle));
            out.write("});\n});});");
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
            out.write(")\">");
        }

        out.write("<img height=\"16\" width=\"16\" src=\"");
        out.write(h(null==comment ? unflagSrc : flagSrc));
        out.write("\"");
        if (comment == null && Boolean.TRUE == canUpdate && null != objectId)
            comment = defaultTitle;
        out.write(" title=\"");
        out.write(h(comment));
        out.write("\"");
        out.write(" flagId=\"");
        out.write(h(objectId));
        out.write("\"");
        out.write(">");
        if (canUpdate)
        {
            out.write("</a>");
        }
    }


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
