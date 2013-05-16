/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.list.view;

import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.list.controllers.ListController;

import java.io.File;
import java.util.Set;
import java.util.Collections;

/**
 * User: adam
 * Date: Feb 12, 2008
 * Time: 1:52:50 PM
 */
public class AttachmentDisplayColumn extends AbstractFileDisplayColumn
{
    private ColumnInfo _colEntityId;

    public AttachmentDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override
    public String renderURL(RenderContext ctx)
    {
        if (null == _colEntityId)
            return null;

        String filename = (String)getValue(ctx);
        String entityId = (String)_colEntityId.getValue(ctx);
        return ListController.getDownloadURL(ctx.getContainer(), entityId, filename).getLocalURIString();
    }

    @Override
    protected String getFileName(Object value)
    {
        if(value instanceof File)
            return ((File) value).getName();
        else if (value instanceof String)
            return (String)value;
        else
            return null;
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        TableInfo table = getBoundColumn().getParentTable();
        FieldKey currentKey = FieldKey.fromString(getBoundColumn().getName());
        FieldKey parentKey = currentKey.getParent();

        FieldKey entityKey = new FieldKey(parentKey, "EntityId");

        _colEntityId = QueryService.get().getColumns(table, Collections.singleton(entityKey)).get(entityKey);

        if (null != _colEntityId)
            columns.add(_colEntityId);
    }
}
