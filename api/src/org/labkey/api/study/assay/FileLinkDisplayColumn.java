/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.data.DataColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.io.Writer;
import java.io.IOException;
import java.io.File;

/**
 * User: brittp
* Date: Oct 23, 2007
* Time: 2:08:29 PM
*/
public class FileLinkDisplayColumn extends DataColumn
{
    private StringExpression _url;
    private PropertyDescriptor _pd;
    private ActionURL _baseURL;
    private ColumnInfo _objectIdColumn;

    public FileLinkDisplayColumn(ColumnInfo col, PropertyDescriptor pd, ActionURL baseURL)
    {
        super(col);
        _pd = pd;
        _baseURL = baseURL;
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        FieldKey thisFieldKey = FieldKey.fromString(getColumnInfo().getName());
        List<FieldKey> keys = new ArrayList<FieldKey>();

        FieldKey objectIdKey = thisFieldKey.getParent();
        keys.add(objectIdKey);
        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(getColumnInfo().getParentTable(), keys);

         _objectIdColumn = cols.get(objectIdKey);
        columns.add(_objectIdColumn);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (_url == null)
        {
            ActionURL url = _baseURL.clone();
            url.addParameter("propertyId", _pd.getPropertyId());
            _url = StringExpressionFactory.create(url.toString() + "&objectId=${" + _objectIdColumn.getAlias() + "}", true);
        }

        String path = (String) getValue(ctx);
        if (path != null)
        {
            File file = new File(path);
            if (file.exists())
            {
                out.write("<a href=\"" + _url.eval(ctx) + "\" title=\"Download attached file\">" +
                    PageFlowUtil.filter(file.getName()) + "</a>");
            }
            else
                out.write(file.getName());
            return;
        }
        super.renderGridCellContents(ctx, out);
    }
}
