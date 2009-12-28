package org.labkey.api.study.assay;

import org.labkey.api.util.FileType;
import org.labkey.api.exp.api.DataType;

/**
 * User: jeckels
 * Date: Dec 22, 2009
 */
public class AssayDataType extends DataType
{
    private final String _role;
    private final FileType _fileType;

    public AssayDataType(String namespacePrefix, FileType fileType)
    {
        this(namespacePrefix, fileType, "Data");
    }

    public AssayDataType(String namespacePrefix, FileType fileType, String role)
    {
        super(namespacePrefix);
        _fileType = fileType;
        _role = role;
    }

    public FileType getFileType()
    {
        return _fileType;
    }

    public String getRole()
    {
        return _role;
    }
}
