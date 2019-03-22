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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.GUID;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Uses LABKEY.Ext.CalloutTip to provide additional details, summoned via AJAX
 *
 * User: jeckels
 * Date: May 14, 2012
 */
public class AJAXDetailsDisplayColumn extends DataColumn
{
    @NotNull private final Map<String, FieldKey> _urlParams;
    private final JSONObject _properties;
    @Nullable private DetailsURL _detailsURL;

    private Set<FieldKey> _requiredValues = new HashSet<>();

    public AJAXDetailsDisplayColumn(@NotNull ColumnInfo col, @Nullable ActionURL detailsURL, @NotNull JSONObject properties)
    {
        this(col, detailsURL, Collections.emptyMap(), properties);
    }

    public AJAXDetailsDisplayColumn(@NotNull ColumnInfo col, @Nullable ActionURL url, @NotNull Map<String, FieldKey> urlParams, @NotNull JSONObject properties, @NotNull FieldKey containerFieldKey)
    {
        this(col, url, urlParams, properties);

        if (_detailsURL != null)
            _detailsURL.setContainerContext(new ContainerContext.FieldKeyContext(containerFieldKey));
    }

    /**
     * @param col base ColumnInfo
     * @param url URL with any required static parameters
     * @param urlParams parameters that will be swapped in based on the row of data being rendered
     * @param properties config passed to LABKEY.Ext.CalloutTip, a subclass of Ext.Tooltip
     */
    public AJAXDetailsDisplayColumn(@NotNull ColumnInfo col, @Nullable ActionURL url, @NotNull Map<String, FieldKey> urlParams, @NotNull JSONObject properties)
    {
        super(col);
        _urlParams = urlParams;
        _properties = properties;
        _detailsURL = url == null ? null : new DetailsURL(url, urlParams);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String evaluatedURL = null;
        if (_detailsURL != null)
        {
            evaluatedURL = _detailsURL.eval(ctx);
        }

        boolean hasAllRequiredValues = true;
        for (FieldKey requiredValue : _requiredValues)
        {
            if (ctx.get(requiredValue) == null)
            {
                hasAllRequiredValues = false;
                break;
            }
        }

        if (evaluatedURL != null && getValue(ctx) != null && hasAllRequiredValues)
        {
            String divId = GUID.makeGUID();
            JSONObject props = new JSONObject(_properties);
            JSONObject autoLoadProp = new JSONObject();
            autoLoadProp.put("url", evaluatedURL);
            props.put("autoLoad", autoLoadProp);
            props.put("target", divId);

            out.write("<span id=\"" + divId + "\">");
            super.renderGridCellContents(ctx, out);
            out.write("</span>");
            out.write("<script type=\"text/javascript\"> \n" +
                "    Ext.onReady(function () { \n" +
                "        var tip = new LABKEY.ext.CalloutTip( \n" +
                        props.toString(0) +
                "        ); \n" +
                "    }); \n" +
                "    </script> ");
        }
        else if (!hasAllRequiredValues)
        {
            StringExpression url = getURLExpression();
            setURLExpression(null);
            super.renderGridCellContents(ctx, out);
            setURLExpression(url);
        }
        else
        {
            super.renderGridCellContents(ctx, out);
        }
    }

    /** Require that a column have a non-null value in order to render the link and AJAX behavior for a given row */
    protected void addRequiredValue(FieldKey fieldKey)
    {
        _requiredValues.add(fieldKey);
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.addAll(_urlParams.values());
    }

    @NotNull
    @Override
    public Set<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("clientapi/ext3"));
        return resources;
    }
}
