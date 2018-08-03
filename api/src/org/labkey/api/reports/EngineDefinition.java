package org.labkey.api.reports;

import org.json.JSONObject;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.springframework.beans.MutablePropertyValues;

import java.util.Map;

public class EngineDefinition implements ExternalScriptEngineDefinition, CustomApiForm
{
    String _key;
    String _name;
    String[] _extensions;
    String _languageName;
    String _languageVersion;
    String _exePath;
    String _exeCommand;
    String _outputFileName;
    String _machine;
    int    _port;
    String _user;
    String _password;
    PathMapper _pathMap;
    boolean _enabled;
    boolean _external;
    boolean _remote;
    boolean _docker;
    boolean _pandocEnabled;

    public String getKey()
    {
        return _key;
    }

    public void setKey(String key)
    {
        _key = key;
    }

    public String getName()
    {
        return _name;
    }

    public String[] getExtensions()
    {
        return _extensions;
    }

    public String getLanguageName()
    {
        return _languageName;
    }

    public String getLanguageVersion()
    {
        return _languageVersion;
    }

    public String getExePath()
    {
        return _exePath;
    }

    public String getExeCommand()
    {
        return _exeCommand;
    }

    public String getOutputFileName()
    {
        return _outputFileName;
    }

    public String getMachine()
    {
        return _machine;
    }

    public int getPort()
    {
        return _port;
    }

    public String getUser()
    {
        return _user;
    }

    public String getPassword()
    {
        return _password;
    }

    public void setOutputFileName(String outputFileName)
    {
        _outputFileName = outputFileName;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void setExtensions(String[] extensions)
    {
        _extensions = extensions;
    }

    public void setLanguageName(String languageName)
    {
        _languageName = languageName;
    }

    public void setLanguageVersion(String languageVersion)
    {
        _languageVersion = languageVersion;
    }

    public void setExePath(String exePath)
    {
        _exePath = exePath;
    }

    public void setExeCommand(String exeCommand)
    {
        _exeCommand = exeCommand;
    }

    public boolean isEnabled()
    {
        return _enabled;
    }

    public void setEnabled(boolean enabled)
    {
        _enabled = enabled;
    }

    public boolean isRemote() { return _remote; }

    public void setRemote(boolean remote) {_remote = remote; }

    public boolean isExternal()
    {
        return _external;
    }

    public void setExternal(boolean external)
    {
        _external = external;
    }

    public void setDocker(boolean docker)
    {
        _docker = docker;
    }

    public boolean isDocker()
    {
        return _docker;
    }

    public void setPort(int port)
    {
        _port = port;
    }

    public void setMachine(String machine)
    {
        _machine = machine;
    }

    public void setUser(String user)
    {
        _user = user;
    }

    public void setPassword(String password)
    {
        _password = password;
    }

    public void setPandocEnabled(boolean pandocEnabled)
    {
        _pandocEnabled = pandocEnabled;
    }

    public boolean isPandocEnabled()
    {
        return _pandocEnabled;
    }

    @Override
    public PathMapper getPathMap()
    {
        return _pathMap;
    }

    @Override
    public void setPathMap(PathMapper pathMap)
    {
        _pathMap = pathMap;
    }

    @Override
    public void bindProperties(Map<String, Object> props)
    {
        // Use default binding for most fields
        MutablePropertyValues params = new MutablePropertyValues(props);
        BaseViewAction.defaultBindParameters(this, "form", params);

        // Handle pathMap
        JSONObject jsonPathMap = (JSONObject)props.get("pathMap");
        if (jsonPathMap != null)
            _pathMap = PathMapperImpl.fromJSON(jsonPathMap, true /*trackValidationErrors*/);
    }
}
