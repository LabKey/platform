/*
 * Copyright (c) 2012-2026 LabKey Corporation
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
import org.labkey.api.util.DOM;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HttpView;
import org.labkey.api.writer.HtmlWriter;

import java.util.Map;

import static org.labkey.api.util.DOM.Attribute.title;
import static org.labkey.api.util.DOM.I;
import static org.labkey.api.util.DOM.SCRIPT;
import static org.labkey.api.util.DOM.at;

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

    protected String renderFlagScript(RenderContext ctx, HtmlWriter out)
    {
        if (null != setFlagFn)
            return setFlagFn;

        String dataRegionName = null == ctx.getCurrentRegion() ? null : ctx.getCurrentRegion().getName();
        String dr = dataRegionName == null ? "" : AliasManager.makeLegalName(dataRegionName, getColumnInfo().getSqlDialect()).replace("_","");

        setFlagFn = "__setFlag" + dr + "_" + getUnique(ctx);

        try
        {
            String script =
                "var " + setFlagFn + ";" +
                "LABKEY.requiresScript('internal/flagColumn', function() {" +
                setFlagFn + " = LABKEY.internal.FlagColumn._showDialog({";

            if (null != endpoint)
                script +=
                "url: " + PageFlowUtil.jsString(endpoint) + ", ";

            if (null != jsConvertPKToLSID)
                script +=
                "  translatePrimaryKey : " + jsConvertPKToLSID + ", ";

            script +=
                "  dataRegionName: " + PageFlowUtil.jsString(dataRegionName) + ", " +
                "  flagEnabledCls: " + PageFlowUtil.jsString(flagEnabledCls()) + ", " +
                "  flagDisabledCls: " + PageFlowUtil.jsString(flagDisabledCls()) + ", " +
                "  imgTitle: " + PageFlowUtil.jsString(defaultTitle) +
                "});\n});";

            SCRIPT(JavaScriptFragment.unsafe(script)).appendTo(out);

            HttpView.currentPageConfig().addHandlerForQuerySelector("A." + setFlagFn, "click", "return " + setFlagFn + "(this.dataset['objectid']);");

            return setFlagFn;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    protected void renderFlag(RenderContext ctx, HtmlWriter out)
    {
        renderFlagScript(ctx, out);
        Object boundValue = getColumnInfo().getValue(ctx);
        if (boundValue == null)
            return;

        if (getDisplayColumn() instanceof FlagColumn flagCol)
        {
            String comment = (String) flagCol.getValue(ctx);
            String objectId = (String) getValue(ctx);
            if (objectId == null)
                return;
            _renderFlag(ctx, out, objectId, comment);
        }
    }

    private Boolean canUpdate = null;

    protected void _renderFlag(RenderContext ctx, HtmlWriter out, String objectId, String comment)
    {
        renderFlagScript(ctx, out);

        if (null == canUpdate)
            canUpdate = ctx.getViewContext().hasPermission(UpdatePermission.class);

        String cl = null == comment ? flagDisabledCls() : flagEnabledCls();

        if (comment == null && canUpdate && null != objectId)
            comment = defaultTitle;

        DOM.Renderable i = I(
            at(title, comment).data("flagid", objectId).cl(cl)
        );

        if (canUpdate && null != objectId)
        {
            out.write(LinkBuilder.simpleLink(i).href("#").addClass(setFlagFn).style("color: #aaaaaa").attributes(Map.of("data-objectid", objectId)));
        }
        else
        {
            i.appendTo(out);
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
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        renderFlag(ctx, out);
    }

    @Override
    protected Object getInputValue(RenderContext ctx)
    {
        ColumnInfo displayField = getColumnInfo().getDisplayField();
        return displayField == null ? null : displayField.getValue(ctx);
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        // never return null
        return StringUtils.trimToEmpty((String)super.getDisplayValue(ctx));
    }

    @Override
    public Object getJsonValue(RenderContext ctx)
    {
        return super.getDisplayValue(ctx);
    }
}
