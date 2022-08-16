/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.CoreUrls;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.AbstractFileDisplayColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RemappingDisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URIUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
* Date: Oct 23, 2007
* Time: 2:08:29 PM
*/
public class FileLinkDisplayColumn extends AbstractFileDisplayColumn
{
    public static class Factory implements RemappingDisplayColumnFactory
    {
        private PropertyDescriptor _pd;
        private Container _container;
        private DetailsURL _detailsUrl;
        private SchemaKey _schemaKey;
        private String _queryName;
        private FieldKey _pkFieldKey;
        private FieldKey _objectURIFieldKey;

        public Factory(PropertyDescriptor pd, Container c, @NotNull SchemaKey schemaKey, @NotNull String queryName, @NotNull FieldKey pkFieldKey)
        {
            _pd = pd;
            _container = c;
            _schemaKey = schemaKey;
            _queryName = queryName;
            _pkFieldKey = pkFieldKey;
        }

        public Factory(PropertyDescriptor pd, Container c, @NotNull FieldKey lsidColumnFieldKey)
        {
            _pd = pd;
            _container = c;
            _objectURIFieldKey = lsidColumnFieldKey;
        }

        public Factory(DetailsURL detailsURL, Container c, @NotNull FieldKey pkFieldKey)
        {
            _detailsUrl = detailsURL;
            _container = c;
            _pkFieldKey = pkFieldKey;
        }

        @Override
        public Factory remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap)
        {
            Factory remapped = this.clone();
            if (remapped._pkFieldKey != null)
                remapped._pkFieldKey = FieldKey.remap(_pkFieldKey, parent, remap);
            if (remapped._objectURIFieldKey != null)
                remapped._objectURIFieldKey = FieldKey.remap(_objectURIFieldKey, parent, remap);
            return remapped;
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo col)
        {
            if (_pd == null && _detailsUrl != null)
                return new FileLinkDisplayColumn(col, _detailsUrl, _container, _pkFieldKey);
            else if (_pkFieldKey != null)
                return new FileLinkDisplayColumn(col, _pd, _container, _schemaKey, _queryName, _pkFieldKey);
            else if (_container != null)
                return new FileLinkDisplayColumn(col, _pd, _container, _objectURIFieldKey);
            else
                throw new IllegalArgumentException("Cannot create a renderer from the specified configuration properties");
        }

        @Override
        public FileLinkDisplayColumn.Factory clone()
        {
            try
            {
                return (Factory)super.clone();
            }
            catch (CloneNotSupportedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private Container _container;
    private FieldKey _pkFieldKey;
    private FieldKey _objectURIFieldKey;

    /** Use schemaName/queryName and pk FieldKey value to resolve File in CoreController.DownloadFileLinkAction. */
    public FileLinkDisplayColumn(ColumnInfo col, PropertyDescriptor pd, Container container, @NotNull SchemaKey schemaKey, @NotNull String queryName, @NotNull FieldKey pkFieldKey)
    {
        super(col);
        _container = container;
        _pkFieldKey = pkFieldKey;

        if (pd.getURL() == null)
        {
            // Don't stomp over an explicitly configured URL on this column
            StringBuilder sb = new StringBuilder("/core/downloadFileLink.view?propertyId=");
            sb.append(pd.getPropertyId());
            sb.append("&schemaName=");
            sb.append(PageFlowUtil.encodeURIComponent(schemaKey.toString()));
            sb.append("&queryName=");
            sb.append(PageFlowUtil.encodeURIComponent(queryName));
            sb.append("&pk=${");
            sb.append(pkFieldKey);
            sb.append("}");
            ContainerContext context = new ContainerContext.FieldKeyContext(new FieldKey(pkFieldKey.getParent(), "Folder"));
            setURLExpression(DetailsURL.fromString(sb.toString(), context));
        }
    }

    /** Use LSID FieldKey value as ObjectURI to resolve File in CoreController.DownloadFileLinkAction. */
    public FileLinkDisplayColumn(ColumnInfo col, PropertyDescriptor pd, Container container, @NotNull FieldKey objectURIFieldKey)
    {
        super(col);
        _container = container;
        _objectURIFieldKey = objectURIFieldKey;

        if (pd.getURL() == null)
        {
            // Don't stomp over an explicitly configured URL on this column
            DetailsURL url = new DetailsURL(PageFlowUtil.urlProvider(CoreUrls.class).getDownloadFileLinkBaseURL(container, pd), "objectURI", objectURIFieldKey);
            setURLExpression(url);
        }
    }

    public FileLinkDisplayColumn(ColumnInfo col, DetailsURL detailsURL, Container container, @NotNull FieldKey pkFieldKey)
    {
        super(col);
        _container = container;
        _pkFieldKey = pkFieldKey;

        setURLExpression(detailsURL);
    }

    @Override
    protected Object getInputValue(RenderContext ctx)
    {
        ColumnInfo col = getColumnInfo();
        Object val = null;
        TableViewForm viewForm = ctx.getForm();

        if (col != null)
        {
            if (null != viewForm && viewForm.contains(this, ctx))
            {
                val = viewForm.get(getFormFieldName(ctx));
            }
            else if (ctx.getRow() != null)
                val = col.getValue(ctx);
        }

        return val;
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
    protected String getFileName(RenderContext ctx, Object value)
    {
        String result = value == null ? null : StringUtils.trimToNull(value.toString());
        if (result != null)
        {
            File f = FileUtil.getAbsoluteCaseSensitiveFile(new File(result));
            NetworkDrive.ensureDrive(f.getPath());
            result = relativize(f, FileContentService.get().getFileRoot(_container, FileContentService.ContentType.files));
            if (result == null)
            {
                result = relativize(f, FileContentService.get().getFileRoot(_container, FileContentService.ContentType.pipeline));
            }
            if (result == null)
            {
                result = f.getName();
            }
        }
        return result;
    }

    public static String relativize(File f, File fileRoot)
    {
        if (fileRoot != null)
        {
            NetworkDrive.ensureDrive(fileRoot.getPath());
            fileRoot = FileUtil.getAbsoluteCaseSensitiveFile(fileRoot);
            if (URIUtil.isDescendant(fileRoot.toURI(), f.toURI()))
            {
                try
                {
                    return FileUtil.relativize(fileRoot, f, false);
                }
                catch (IOException ignored) {}
            }
        }
        return null;
    }

    @Override
    protected InputStream getFileContents(RenderContext ctx, Object ignore) throws FileNotFoundException
    {
        Object value = getValue(ctx);
        String s = value == null ? null : StringUtils.trimToNull(value.toString());
        if (s != null)
        {
            File f = new File(s);
            if (f.isFile())
                return new FileInputStream(f);
        }
        return null;
    }

    @Override
    protected void renderIconAndFilename(RenderContext ctx, Writer out, String filename, boolean link, boolean thumbnail) throws IOException
    {
        Object value = getValue(ctx);
        String s = value == null ? null : StringUtils.trimToNull(value.toString());
        if (s != null)
        {
            File f;
            if (s.startsWith("file:"))
                f = new File(URI.create(s));
            else
                f = new File(s);

            if (!f.exists())
            {
                String fullPath = PipelineService.get().findPipelineRoot(_container).getRootPath().getAbsolutePath() + File.separator + AssayFileWriter.DIR_NAME + File.separator + value.toString();
                f = new File(fullPath);
            }

            // It's probably a file, so check that first
            if (f.isFile())
            {
                super.renderIconAndFilename(ctx, out, filename, link, thumbnail);
            }
            else if (f.isDirectory())
            {
                super.renderIconAndFilename(ctx, out, filename, Attachment.getFileIcon(".folder"), null, link, false);
            }
            else
            {
                // It's not on the file system anymore, so don't offer a link and tell the user it's unavailable
                super.renderIconAndFilename(ctx, out, filename + " (unavailable)", Attachment.getFileIcon(filename), null, false, false);
            }
        }
        else
        {
            super.renderIconAndFilename(ctx, out, filename, link, thumbnail);
        }
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return getFileName(ctx, super.getDisplayValue(ctx));
    }
}
