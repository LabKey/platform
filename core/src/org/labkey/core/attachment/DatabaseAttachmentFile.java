/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.core.attachment;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.logging.LogHelper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.labkey.api.util.IntegerUtils.asInteger;

public class DatabaseAttachmentFile implements AttachmentFile
{
    private static final Logger LOG = LogHelper.getLogger(DatabaseAttachmentFile.class, "Attachment file stored in the DB");
    private final Attachment _attachment;
    private final String _contentType;
    private final int _fileSize;

    private ResultSet _rs = null;
    private InputStream _is = null;

    public DatabaseAttachmentFile(Attachment attachment) throws IOException
    {
        _attachment = attachment;

        Map<String, Object> map = new SqlSelector(CoreSchema.getInstance().getSchema(), appendFromWhere(SQLFragment.unsafe("SELECT a.Type, a.Size"))).getMap();

        if (null == map)
            throw new FileNotFoundException("Attachment could not be retrieved from database: " + attachment.getName());

        _contentType = (String) map.get("Type");

        int size = asInteger(map.get("Size"));
        _fileSize = (Math.max(size, 0));
    }

    private SQLFragment appendFromWhere(SQLFragment sql)
    {
        CoreSchema core = CoreSchema.getInstance();
        return sql.unsafeAppend(" FROM ").append(core.getTableInfoAttachmentPointers())
            .unsafeAppend(" ap INNER JOIN ").append(core.getTableInfoAttachments())
            .unsafeAppend(" a ON a.RowId = ap.AttachmentId WHERE ap.Container = ? AND ap.Parent = ? AND ap.Name = ?", _attachment.getContainer(), _attachment.getParent(), _attachment.getName());
    }

    @Override
    public String getContentType()
    {
        return _contentType;
    }

    @Override
    public long getSize()
    {
        return _fileSize;
    }

    @Override
    public String getError()
    {
        return null;
    }

    @Override
    public boolean isReopenable()
    {
        return true;
    }

    @Override
    public String getFilename()
    {
        return _attachment.getName();
    }

    // NOTE: ResultSet is left open to allow streaming attachment contents from the database.  closeInputStream() must be called when through.
    @Override
    public InputStream openInputStream() throws IOException
    {
        if (!(_rs == null && _is == null))
            throw new IllegalStateException("InputStream has already been opened");

        try
        {
            _rs = new SqlSelector(CoreSchema.getInstance().getSchema(), appendFromWhere(SQLFragment.unsafe("SELECT a.Document"))).getResultSet(false);

            if (!_rs.next())
                throw new FileNotFoundException("Attachment could not be retrieved from database: " + _attachment.getName());

            _is = _rs.getBinaryStream("document");
            return _is;
        }
        catch(SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (null == _is)
                _rs = ResultSetUtil.close(_rs);
        }
    }

    @Override
    public void closeInputStream()
    {
        if (_is != null)
        {
            IOUtils.closeQuietly(_is);
            _is = null;
            _rs = ResultSetUtil.close(_rs);
        }
        else
            LOG.debug("No input stream is active for this DatabaseAttachmentFile");
    }
}