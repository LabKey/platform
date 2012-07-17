/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Apr 27, 2006
 * Time: 10:21:24 AM
 */
public class ContainerDisplayColumn extends DataColumn
{
    private Container _c;
    private ActionURL _url;
    private final boolean _showPath;
    private final boolean _boundColHasEntityId;

    /**
     * @param showPath if true, show the container's full path. If false, show just its name
     */
    public ContainerDisplayColumn(ColumnInfo col, boolean showPath)
    {
        this(col, showPath, null);
    }

    /**
     * @param showPath if true, show the container's full path. If false, show just its name
     */
    public ContainerDisplayColumn(ColumnInfo col, boolean showPath, ActionURL actionURL)
    {
        this(col, showPath, actionURL, false);
    }

    /**
     * @param showPath if true, show the container's full path. If false, show just its name
     * @param boundColHasEntityId if true, the value of this column will be used as the entityId.  If not, it will resolve to the containers table (for example, container/EntityId)
     */
    public ContainerDisplayColumn(ColumnInfo col, boolean showPath, ActionURL actionURL, boolean boundColHasEntityId)
    {
        super(col);
        _showPath = showPath;
        _boundColHasEntityId = boundColHasEntityId;
        if (actionURL == null)
        {
            _url = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(ContainerManager.getRoot());
        }
        else
        {
            _url = actionURL.clone();
        }
    }

    @Override
    public Object getJsonValue(RenderContext ctx)
    {
        Object result = ctx.get(getBoundColumn().getFieldKey());
        if (result == null)
        {
            // If we couldn't find it by FieldKey, check by alias as well
            result = getBoundColumn().getValue(ctx);
        }

        _c = getContainer(ctx);

        if(_c == null)
            return null;

        return _showPath ? _c.getPath() : result;
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        // Get the container for this row; stash the path in the context so urls can use it
        _c = getContainer(ctx);

        if (_c == null)
        {
            String id = getEntityIdValue(ctx);
            if(id != null)
                return "<deleted>";
            else if (getEntityIdFieldKey(ctx) != null && id == null)
                return "";
            else
                return "<could not resolve container>";
        }
        return _showPath ? _c.getPath() : _c.getTitle();
    }

    //NOTE: custom SQL statements may not container a column named entityId, so we fall back to container if entityId is absent
    private List<FieldKey> getEntityIdFieldKeys()
    {
        List<FieldKey> keys = new ArrayList<FieldKey>();

        if(_boundColHasEntityId)
        {
            keys.add(getBoundColumn().getFieldKey());
        }
        else
        {
            keys.add(new FieldKey(getDisplayColumn().getFieldKey().getParent(), "EntityId"));
            keys.add(new FieldKey(getDisplayColumn().getFieldKey().getParent(), "Container"));
            keys.add(new FieldKey(getDisplayColumn().getFieldKey().getParent(), "Folder"));
        }

        return keys;
    }

    private FieldKey getEntityIdFieldKey(RenderContext ctx)
    {
        for(FieldKey fk : getEntityIdFieldKeys())
        {
            if(ctx.containsKey(fk) && (ctx.get(fk) == null || ctx.get(fk) instanceof String))
            {
                return fk;
            }
        }
        return null;
    }

    private String getEntityIdValue(RenderContext ctx)
    {
        FieldKey fk = getEntityIdFieldKey(ctx);
        if(fk == null)
            return null;

        return ctx.get(fk) == null ? null : (String)ctx.get(fk);
    }

    private Container getContainer(RenderContext ctx)
    {
        String id = getEntityIdValue(ctx);

        _c = id == null ? null : ContainerManager.getForId(id);
        return _c;
    }


    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.addAll(getEntityIdFieldKeys());
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        // Do this first to make sure we've looked up the container if needed
        getDisplayValue(ctx);
        // Don't render link if container is deleted
        if (null == _c)
        {
            out.write(getFormattedValue(ctx));
        }
        else
        {
            if (_url != null)
            {
                _url.setContainer(_c);
            }
            super.renderGridCellContents(ctx, out);
        }
    }

    @Override
    public String renderURL(RenderContext ctx)
    {
        Container c = getContainer(ctx);

        if(c == null)
            return null;

        return c.getStartURL(ctx.getViewContext().getUser()).toString();
    }

    public String getFormattedValue(RenderContext ctx)
    {
        // Do this before outputting the URL as it makes sure that we've looked up the container object
        String displayValue = getDisplayValue(ctx).toString();

        StringBuilder sb = new StringBuilder();
        if (_c != null && _url != null)
        {
            sb.append("<a href=\"");
            sb.append(_url.getLocalURIString());
            sb.append("\">");
        }
        sb.append(PageFlowUtil.filter(displayValue));
        if (_c != null && _url != null)
        {
            sb.append("</a>");
        }
        return sb.toString();
    }

    public boolean isFilterable()
    {
        return !_showPath;
    }
}
