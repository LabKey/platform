package org.labkey.api.exp.api;

import org.labkey.api.util.URLHelper;
import org.labkey.api.exp.Lsid;

public class DataType
{
    protected String _namespacePrefix;
    public DataType(String namespacePrefix)
    {
        _namespacePrefix = namespacePrefix;
    }
    public String getNamespacePrefix()
    {
        return _namespacePrefix;
    }
    public URLHelper getDetailsURL(ExpData dataObject)
    {
        return null;
    }
    public String urlFlag(boolean flagged)
    {
        return null;
    }

    public boolean matches(Lsid lsid)
    {
        return lsid != null && lsid.getNamespacePrefix().equals(_namespacePrefix);
    }
}
