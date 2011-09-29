package org.labkey.core.admin;

/**
* User: jeckels
* Date: Sep 26, 2011
*/
public class FileSettingsForm
{
    private String _rootPath;
    private boolean _upgrade;

    public String getRootPath()
    {
        return _rootPath;
    }

    public void setRootPath(String rootPath)
    {
        _rootPath = rootPath;
    }

    public boolean isUpgrade()
    {
        return _upgrade;
    }

    public void setUpgrade(boolean upgrade)
    {
        _upgrade = upgrade;
    }
}
