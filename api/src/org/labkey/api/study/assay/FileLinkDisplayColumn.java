/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import org.labkey.api.admin.CoreUrls;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.AbstractFileDisplayColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * User: brittp
* Date: Oct 23, 2007
* Time: 2:08:29 PM
*/
public class FileLinkDisplayColumn extends AbstractFileDisplayColumn
{
    private FieldKey _objectURIFieldKey;

    public FileLinkDisplayColumn(ColumnInfo col, PropertyDescriptor pd, Container container, FieldKey objectURIFieldKey)
    {
        super(col);

        _objectURIFieldKey = objectURIFieldKey;

        setURLExpression(new DetailsURL(PageFlowUtil.urlProvider(CoreUrls.class).getDownloadFileLinkBaseURL(container, pd), "objectURI", objectURIFieldKey));
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.add(_objectURIFieldKey);
    }

    @Override
    protected String getFileName(Object value)
    {
        if (value instanceof String)
        {
            return new File((String)value).getName();
        }
        return null;
    }

    @Override
    protected void renderIconAndFilename(RenderContext ctx, Writer out, String filename, boolean link) throws IOException
    {
        if (isDirectory(getValue(ctx)))
            super.renderIconAndFilename(ctx, out, filename, Attachment.getFileIcon(".folder"), link);
        else
            super.renderIconAndFilename(ctx, out, filename, link);
    }

    private boolean isDirectory(Object value)
    {
        return new File(value.toString()).isDirectory();
    }
}
