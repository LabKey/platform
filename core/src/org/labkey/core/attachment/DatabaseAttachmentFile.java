/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.util.ResultSetUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: adam
 * Date: Fed 22, 2008
 * Time: 8:57:17 PM
 */
public class DatabaseAttachmentFile implements AttachmentFile
{
    private final Attachment _attachment;
    private final String _contentType;
    private final int _fileSize;

    private ResultSet _rs = null;
    private InputStream _is = null;

    private static final CoreSchema core = CoreSchema.getInstance();
    private static final String _sqlDocumentTypeAndSize = "SELECT DocumentType, DocumentSize FROM " + core.getTableInfoDocuments() + " WHERE Parent = ? AND DocumentName = ?";
    private static final String _sqlDocument = "SELECT Document FROM " + core.getTableInfoDocuments() + " WHERE Parent = ? AND DocumentName = ?";

    public DatabaseAttachmentFile(Attachment attachment) throws IOException
    {
        _attachment = attachment;

        Map<String, Object> map = new SqlSelector(core.getSchema(), _sqlDocumentTypeAndSize, attachment.getParent(), attachment.getName()).getMap();

        if (null == map)
            throw new FileNotFoundException("Attachment could not be retrieved from database: " + attachment.getName());

        _contentType = (String) map.get("DocumentType");

        int size = (Integer)map.get("DocumentSize");
        _fileSize = (size > 0 ? size : 0);
    }

    public String getContentType()
    {
        return _contentType;
    }

    public long getSize()
    {
        return _fileSize;
    }

    public String getError()
    {
        return null;
    }

    public String getFilename()
    {
        return _attachment.getName();
    }

    // NOTE: ResultSet is left open to allow streaming attachment contents from the database.  closeInputStream() must be called when through.
    public InputStream openInputStream() throws IOException
    {
        if (!(_rs == null && _is == null))
            throw new IllegalStateException("InputStream has already been opened");

        try
        {
            _rs = new SqlSelector(core.getSchema(), _sqlDocument, _attachment.getParent(), _attachment.getName()).getResultSet(false);

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

    public void closeInputStream() throws IOException
    {
        IOUtils.closeQuietly(_is);
        _is = null;
        _rs = ResultSetUtil.close(_rs);
    }
}