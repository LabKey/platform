/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import java.io.Writer;
import java.io.IOException;
import java.util.Set;
import java.util.Collections;
import java.util.Map;

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
    private ColumnInfo _entityIdColumn;

    private String _errorMessage;

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
        super(col);
        _showPath = showPath;
        if (actionURL == null)
        {
            _url = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(ContainerManager.getRoot());
        }
        else
        {
            _url = actionURL.clone();
        }
    }

    public void setEntityIdColumn(ColumnInfo entityIdColumn)
    {
        _entityIdColumn = entityIdColumn;
    }

    @Override
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        if (_entityIdColumn == null)
        {
            FieldKey key = FieldKey.fromString(getBoundColumn().getName());
            FieldKey entityKey = new FieldKey(key.getParent(), "EntityId");
            Map<FieldKey,ColumnInfo> cols = QueryService.get().getColumns(getBoundColumn().getParentTable(), Collections.singleton(entityKey));
            _entityIdColumn = cols.get(entityKey);
            if (_entityIdColumn == null)
            {
                _errorMessage = "ERROR: Unable to resolve container column " + entityKey.toString();
            }
            else
            {
                columns.add(_entityIdColumn);
            }
        }
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        // Get the container for this row; stash the path in the context so urls can use it
        if (_entityIdColumn != null)
        {
            String id = (String)_entityIdColumn.getValue(ctx);
            _c = ContainerManager.getForId(id);
        }

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

    public String getFormattedValue(RenderContext ctx)
    {
        if (_errorMessage != null)
        {
            return PageFlowUtil.filter(_errorMessage);
        }
        StringBuilder sb = new StringBuilder();
        if (_url != null)
        {
            sb.append("<a href=\"");
            sb.append(_url.getLocalURIString());
            sb.append("\">");
        }
        if (_c == null)
        {
            sb.append(PageFlowUtil.filter("<deleted>"));
        }
        else
        {
            sb.append(PageFlowUtil.filter(_showPath ? _c.getPath() : _c.getName()));
        }
        if (_url != null)
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
