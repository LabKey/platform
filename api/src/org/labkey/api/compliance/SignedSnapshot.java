/*
 * Copyright (c) 2017 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.api.compliance;

import org.labkey.api.attachments.ByteArrayAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.query.QueryForm;
import org.labkey.api.security.User;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by davebradlee on 7/6/17.
 */
public class SignedSnapshot
{
    private int _rowId;
    private Container _container;
    private String _schemaName;
    private String _tableName;
    private String _signedFilename;
    private int _rows;
    private long _filesize;
    private String _reason;
    private User _signedBy;
    private Date _signed;
    private String _entityId;
    private String _hash;
    private String _ownerEntityId;
    private String _attachmentType;

    public SignedSnapshot()
    {
    }

    public SignedSnapshot(Container container, User user, QueryForm form, String entityId, ByteArrayAttachmentFile file, Date date, int rows, String reason)
    {
        _container = container;
        _schemaName = form.getSchemaName();
        _tableName = form.getQueryName();
        _signedBy = user;
        _entityId = entityId;
        _signedFilename = file.getFilename();
        _filesize = file.getSize();
        _signed = date;
        _rows = rows;
        _reason = reason;
    }

    // factory method
    public static SignedSnapshot fromMap(Map<String, Object> map)
    {
        ObjectFactory<SignedSnapshot> factory = ObjectFactory.Registry.getFactory(SignedSnapshot.class);
        return factory.fromMap(map);
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getTableName()
    {
        return _tableName;
    }

    public void setTableName(String tableName)
    {
        _tableName = tableName;
    }

    public String getSignedFilename()
    {
        return _signedFilename;
    }

    public void setSignedFilename(String signedFilename)
    {
        _signedFilename = signedFilename;
    }

    public int getRows()
    {
        return _rows;
    }

    public void setRows(int rows)
    {
        _rows = rows;
    }

    public long getFilesize()
    {
        return _filesize;
    }

    public void setFilesize(long filesize)
    {
        _filesize = filesize;
    }

    public String getReason()
    {
        return _reason;
    }

    public void setReason(String reason)
    {
        _reason = reason;
    }

    public User getSignedBy()
    {
        return _signedBy;
    }

    public void setSignedBy(User user)
    {
        _signedBy = user;
    }

    public Date getSigned()
    {
        return _signed;
    }

    public void setSigned(Date signed)
    {
        _signed = signed;
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }

    public String getHash()
    {
        return _hash;
    }

    public void setHash(String hash)
    {
        _hash = hash;
    }

    public String getOwnerEntityId()
    {
        return _ownerEntityId;
    }

    public void setOwnerEntityId(String ownerEntityId)
    {
        _ownerEntityId = ownerEntityId;
    }

    public String getAttachmentType()
    {
        return _attachmentType;
    }

    public void setAttachmentType(String attachmentType)
    {
        _attachmentType = attachmentType;
    }

    public Map<String, Object> toMap()
    {
        Map<String, Object> props = new HashMap<>();
        props.put("RowId", getRowId());
        props.put("SchemaName", getSchemaName());
        props.put("TableName", getTableName());
        props.put("SignedFilename", getSignedFilename());
        props.put("Rows", getRows());
        props.put("Filesize", getFilesize());
        props.put("Reason", getReason());
        props.put("SignedBy", getSignedBy());
        props.put("Signed", getSigned());
        props.put("EntityId", getEntityId());
        props.put("Container", getContainer());
        props.put("Hash", getHash());
        props.put("OwnerEntityId", getOwnerEntityId());
        props.put("AttachmentType", getAttachmentType());
        return props;
    }
}
