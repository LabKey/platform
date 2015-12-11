/*
 * Copyright (c) 2015 LabKey Corporation
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

import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentParentImpl;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.query.FieldKey;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Set;

/**
 * User: adam
 * Date: Feb 12, 2008
 *
 * Renders attachment popup and download link using the underlying ColumnInfo's URL.
 */
public class AttachmentDisplayColumn extends AbstractFileDisplayColumn
{
    public AttachmentDisplayColumn(ColumnInfo col)
    {
        super(col);
        assert col.getURL() != null: "Attachment download column URL required";
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.add(FieldKey.fromParts("entityId"));
    }

    @Override
    protected String getFileName(Object value)
    {
        if (value instanceof File)
            return ((File) value).getName();
        else if (value instanceof String)
            return (String)value;
        else
            return null;
    }

    @Override
    public Class getDisplayValueClass()
    {
        ColumnInfo displayColumn = getDisplayColumn();
        if (displayColumn.getPropertyType() != null)
            return displayColumn.getPropertyType().getJavaType();
        return super.getDisplayValueClass();
    }

    @Override
    protected InputStream getFileContents(RenderContext ctx, Object value) throws FileNotFoundException
    {
        String entityIdValue = null;
        String downloadUrl = getURLExpression().eval(ctx);
        String[] parts = downloadUrl.split("entityId=");
        if (parts.length > 1)
        {
            String[] paramParts = parts[1].split("&");
            if (paramParts.length > 0)
                entityIdValue = paramParts[0];
        }

        String filename = (String)getValue(ctx);

        if (null == filename || entityIdValue == null)
            return null;

        AttachmentParent parent = new AttachmentParentImpl(entityIdValue, ctx.getContainer());
        return AttachmentService.get().getInputStream(parent, filename);
    }

}
