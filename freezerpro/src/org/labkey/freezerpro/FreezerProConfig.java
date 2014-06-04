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
    private String _reloadDate;
    private String _metadata;
    private int _reloadUser;

    public enum Options
    {
        url,
        user,
        password,
        reloadInterval,
        enableReload,
        importUserFields,
        reloadDate,
        metadata,
        reloadUser,
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

    public String getReloadDate()
    {
        return _reloadDate;
    }

    public void setReloadDate(String reloadDate)
    {
        _reloadDate = reloadDate;
    }

    public boolean isImportUserFields()
    {
        return _importUserFields;
    }

    public void setImportUserFields(boolean importUserFields)
    {
        _importUserFields = importUserFields;
    }

    public String getMetadata()
    {
        return _metadata;
    }

    public void setMetadata(String metadata)
    {
        _metadata = metadata;
    }
}
