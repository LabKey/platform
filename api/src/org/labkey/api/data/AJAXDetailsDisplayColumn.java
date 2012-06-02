package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
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

    public AJAXDetailsDisplayColumn(@NotNull ColumnInfo col, @Nullable ActionURL detailsURL, @NotNull JSONObject properties)
    {
        this(col, detailsURL, Collections.<String, FieldKey>emptyMap(), properties);
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
        if (evaluatedURL != null)
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
        else
        {
            super.renderGridCellContents(ctx, out);
        }
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.addAll(_urlParams.values());
    }
}
