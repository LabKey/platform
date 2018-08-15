package org.labkey.core.reports;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.data.Entity;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.springframework.beans.MutablePropertyValues;

import java.util.Map;

public class ExternalScriptEngineDefinitionImpl extends Entity implements ExternalScriptEngineDefinition, CustomApiForm
{
    private Integer _rowId;
    private String _name;
    private boolean _enabled;
    private String _description;
    private ExternalScriptEngineDefinition.Type _type;
    private String _configuration;

    private String _extensions;
    private String _languageName;
    private String _languageVersion;
    private String _exePath;
    private String _exeCommand;
    private String _outputFileName;
    private String _machine;
    private int _port;
    private String _user;
    private String _password;
    private String _pathMap;
    private boolean _external;
    private boolean _remote;
    private boolean _docker;
    private boolean _pandocEnabled;
    private boolean _default;
    private PathMapper _pathMapper;

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

//    @Override
    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    @Override
    public Type getType()
    {
        return _type;
    }

    public void setType(Type type)
    {
        _type = type;
    }

    public String getConfiguration()
    {
        if (_configuration == null)
        {
            JSONObject json = new JSONObject();

            addIfNotNull(json, "extensions", _extensions);
            addIfNotNull(json, "languageName", getLanguageName());
            addIfNotNull(json, "languageVersion", getLanguageVersion());
            addIfNotNull(json, "exePath", getExePath());
            addIfNotNull(json, "exeCommand", getExeCommand());
            addIfNotNull(json, "outputFileName", getOutputFileName());
            addIfNotNull(json, "machine", getMachine());
            addIfNotNull(json, "port", getPort());
            addIfNotNull(json, "user", getUser());
            addIfNotNull(json, "password", getPassword());
            addIfNotNull(json, "external", isExternal());
            addIfNotNull(json, "remote", isRemote());
            addIfNotNull(json, "docker", isDocker());
            addIfNotNull(json, "pandocEnabled", isPandocEnabled());
            addIfNotNull(json, "pathMap", _pathMap);
            addIfNotNull(json, "default", isDefault());

            _configuration = json.toString();
        }
        return _configuration;
    }

    public void setConfiguration(String configuration)
    {
        // parse the JSON object
        JSONObject json = new JSONObject(configuration);

        if (json.has("extensions"))
            setExtensions(json.getString("extensions"));
        if (json.has("languageName"))
            setLanguageName(json.getString("languageName"));
        if (json.has("languageVersion"))
            setLanguageVersion(json.getString("languageVersion"));
        if (json.has("exePath"))
            setExePath(json.getString("exePath"));
        if (json.has("exeCommand"))
            setExeCommand(json.getString("exeCommand"));
        if (json.has("outputFileName"))
            setOutputFileName(json.getString("outputFileName"));
        if (json.has("machine"))
            setMachine(json.getString("machine"));
        if (json.has("port"))
            setPort(json.getInt("port"));
        if (json.has("user"))
            setUser(json.getString("user"));
        if (json.has("password"))
            setPassword(json.getString("password"));
        if (json.has("external"))
            setExternal(json.getBoolean("external"));
        if (json.has("remote"))
            setRemote(json.getBoolean("remote"));
        if (json.has("docker"))
            setDocker(json.getBoolean("docker"));
        if (json.has("pandocEnabled"))
            setPandocEnabled(json.getBoolean("pandocEnabled"));
        if (json.has("pathMap"))
            setPathMap(json.getString("pathMap"));
        if (json.has("default"))
            setDefault(json.getBoolean("default"));

        _configuration = configuration;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public void setName(String name)
    {
        _name = name;
    }

    @Override
    public boolean isEnabled()
    {
        return _enabled;
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        _enabled = enabled;
    }

    @Override
    public String[] getExtensions()
    {
        return StringUtils.split(_extensions, ",");
    }

    public void setExtensions(String extensions)
    {
        _extensions = extensions;
    }

    @Override
    @Deprecated
    public void setExtensions(String[] extensions)
    {
        _extensions = StringUtils.join(extensions, ",");
    }

    @Override
    public String getLanguageName()
    {
        return _languageName;
    }

    @Override
    public void setLanguageName(String languageName)
    {
        _languageName = languageName;
    }

    @Override
    public String getLanguageVersion()
    {
        return _languageVersion;
    }

    @Override
    public void setLanguageVersion(String languageVersion)
    {
        _languageVersion = languageVersion;
    }

    @Override
    public String getExePath()
    {
        return _exePath;
    }

    @Override
    public void setExePath(String exePath)
    {
        _exePath = exePath;
    }

    @Override
    public String getExeCommand()
    {
        return _exeCommand;
    }

    @Override
    public void setExeCommand(String exeCommand)
    {
        _exeCommand = exeCommand;
    }

    @Override
    public String getOutputFileName()
    {
        return _outputFileName;
    }

    @Override
    public void setOutputFileName(String outputFileName)
    {
        _outputFileName = outputFileName;
    }

    @Override
    public String getMachine()
    {
        return _machine;
    }

    @Override
    public void setMachine(String machine)
    {
        _machine = machine;
    }

    @Override
    public int getPort()
    {
        return _port;
    }

    @Override
    public void setPort(int port)
    {
        _port = port;
    }

    @Override
    public String getUser()
    {
        return _user;
    }

    @Override
    public void setUser(String user)
    {
        _user = user;
    }

    @Override
    public String getPassword()
    {
        return _password;
    }

    @Override
    public void setPassword(String password)
    {
        _password = password;
    }

    @Override
    public PathMapper getPathMap()
    {
        if (_pathMapper != null)
            return _pathMapper;
        else if (_pathMap != null)
        {
            JSONObject pathMapJson = new JSONObject(_pathMap);
            return PathMapperImpl.fromJSON(pathMapJson, true);
        }
        return null;
    }

    public void setPathMap(String pathMap)
    {
        _pathMap = pathMap;
    }

    @Override
    public void setPathMapper(PathMapper pathMap)
    {
        if (pathMap != null)
        {
            _pathMapper = pathMap;
            pathMap.getPathMap();
            _pathMap = pathMap.toJSON().toString();
        }
    }

    @Override
    public boolean isExternal()
    {
        return _external;
    }

    @Override
    public void setExternal(boolean external)
    {
        _external = external;
    }

    @Override
    public boolean isRemote()
    {
        return _remote;
    }

    @Override
    public void setRemote(boolean remote)
    {
        _remote = remote;
    }

    @Override
    public boolean isDocker()
    {
        return _docker;
    }

    @Override
    public void setDocker(boolean docker)
    {
        _docker = docker;
    }

    @Override
    public boolean isPandocEnabled()
    {
        return _pandocEnabled;
    }

    @Override
    public void setPandocEnabled(boolean pandocEnabled)
    {
        _pandocEnabled = pandocEnabled;
    }

    @Override
    public boolean isDefault()
    {
        return _default;
    }

    public void setDefault(boolean aDefault)
    {
        _default = aDefault;
    }

    private void addIfNotNull(JSONObject json, String key, Object value)
    {
        if (value != null)
            json.put(key, value);
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
            _pathMap = jsonPathMap.toString();
    }
}
