/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.CoreUrls;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.AbstractFileDisplayColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

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
    private FieldKey _pkFieldKey;

    private FieldKey _objectURIFieldKey;

    /** Use schemaName/queryName and pk FieldKey value to resolve File in CoreController.DownloadFileLinkAction. */
    public FileLinkDisplayColumn(ColumnInfo col, PropertyDescriptor pd, Container container, @NotNull SchemaKey schemaKey, @NotNull String queryName, @NotNull FieldKey pkFieldKey)
    {
        super(col);

        _pkFieldKey = pkFieldKey;

        ActionURL actionURL = PageFlowUtil.urlProvider(CoreUrls.class).getDownloadFileLinkBaseURL(container, pd);
        actionURL.addParameter(QueryParam.schemaName, schemaKey.toString());
        actionURL.addParameter(QueryParam.queryName, queryName);
        DetailsURL url = new DetailsURL(actionURL, "pk", pkFieldKey);
        setURLExpression(url);
    }

    /** Use LSID FieldKey value as ObjectURI to resolve File in CoreController.DownloadFileLinkAction. */
    public FileLinkDisplayColumn(ColumnInfo col, PropertyDescriptor pd, Container container, @NotNull FieldKey objectURIFieldKey)
    {
        super(col);

        _objectURIFieldKey = objectURIFieldKey;

        DetailsURL url = new DetailsURL(PageFlowUtil.urlProvider(CoreUrls.class).getDownloadFileLinkBaseURL(container, pd), "objectURI", objectURIFieldKey);
        setURLExpression(url);
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        if (_pkFieldKey != null)
            keys.add(_pkFieldKey);
        if (_objectURIFieldKey != null)
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
    protected void renderIconAndFilename(RenderContext ctx, Writer out, String filename, boolean link, boolean thumbnail) throws IOException
    {
        Object value = getValue(ctx);
        if (value != null)
        {
            File f = new File(value.toString());
            // It's probably a file, so check that first
            if (f.isFile())
            {
                super.renderIconAndFilename(ctx, out, filename, link, thumbnail);
            }
            else if (f.isDirectory())
            {
                super.renderIconAndFilename(ctx, out, filename, Attachment.getFileIcon(".folder"), link, false);
            }
            else
            {
                // It's not on the file system anymore, so don't offer a link and tell the user it's unavailable
                super.renderIconAndFilename(ctx, out, filename + " (unavailable)", Attachment.getFileIcon(filename), false, false);
            }
        }
        else
        {
            super.renderIconAndFilename(ctx, out, filename, link, thumbnail);
        }
    }
}
