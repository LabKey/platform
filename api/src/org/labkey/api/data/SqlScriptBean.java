package org.labkey.api.data;

/**
* User: adam
* Date: 8/24/13
* Time: 11:16 AM
*/

// TODO: Combine with SqlScript?
public class SqlScriptBean extends Entity
{
    private String _moduleName;
    private String _fileName;

    @SuppressWarnings("UnusedDeclaration")
    public SqlScriptBean()
    {
    }

    public SqlScriptBean(String moduleName, String fileName)
    {
        _moduleName = moduleName;
        _fileName = fileName;
    }

    public String getModuleName()
    {
        return _moduleName;
    }

    public void setModuleName(String moduleName)
    {
        _moduleName = moduleName;
    }

    public String getFileName()
    {
        return _fileName;
    }

    public void setFileName(String fileName)
    {
        _fileName = fileName;
    }
}
