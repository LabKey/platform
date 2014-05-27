package org.labkey.freezerpro;

import org.labkey.api.study.SpecimenTransform;

/**
* Created by klum on 5/27/2014.
*/
public class FreezerProConfig implements SpecimenTransform.ExternalImportConfig
{
    private String _baseServerUrl;
    private String _username;
    private String _password;
    private int _reloadInterval;
    private boolean _enableReload;
    private boolean _importUserFields;

    public enum Options
    {
        url,
        user,
        password,
        reloadInterval,
        enableReload,
        importUserFields,
    }

    public String getBaseServerUrl()
    {
        return _baseServerUrl;
    }

    public void setBaseServerUrl(String baseServerUrl)
    {
        _baseServerUrl = baseServerUrl;
    }

    public String getUsername()
    {
        return _username;
    }

    public void setUsername(String username)
    {
        _username = username;
    }

    public String getPassword()
    {
        return _password;
    }

    public void setPassword(String password)
    {
        _password = password;
    }

    public int getReloadInterval()
    {
        return _reloadInterval;
    }

    public void setReloadInterval(int reloadInterval)
    {
        _reloadInterval = reloadInterval;
    }

    public boolean isEnableReload()
    {
        return _enableReload;
    }

    public void setEnableReload(boolean enableReload)
    {
        _enableReload = enableReload;
    }

    public boolean isImportUserFields()
    {
        return _importUserFields;
    }

    public void setImportUserFields(boolean importUserFields)
    {
        _importUserFields = importUserFields;
    }
}
