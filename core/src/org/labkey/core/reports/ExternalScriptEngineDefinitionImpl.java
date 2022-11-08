/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.core.reports;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.data.Entity;
import org.labkey.api.docker.DockerService;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.Encryption.Algorithm;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.springframework.beans.MutablePropertyValues;

import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

import static org.labkey.api.reports.report.r.RScriptEngine.DOCKER_IMAGE_TYPE;
import static org.labkey.core.reports.ScriptEngineManagerImpl.ENCRYPTION_MIGRATION_HANDLER;

public class ExternalScriptEngineDefinitionImpl extends Entity implements ExternalScriptEngineDefinition, CustomApiForm
{
    // Most definitions don't require encryption, so retrieve AES128 lazily
    static final Supplier<Algorithm> AES = () -> {
        if (!Encryption.isEncryptionPassPhraseSpecified())
            throw new RuntimeException("Unable to save credentials. EncryptionKey has not been specified in " + AppProps.getInstance().getWebappConfigurationFilename());

        return Encryption.getAES128(ENCRYPTION_MIGRATION_HANDLER);
    };

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
    private boolean _changePassword;
    private String _password;
    private String _pathMap;
    private boolean _external;
    private boolean _remote;
    private boolean _docker;
    private boolean _pandocEnabled;
    private boolean _default;
    private boolean _sandboxed;
    private PathMapper _pathMapper;
    private Integer _dockerImageRowId;
    private String _dockerImageConfig;
    private String _remoteUrl;

    @Override
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
            updateConfiguration();
        }
        return _configuration;
    }

    public void updateConfiguration()
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

        if (isChangePassword())
        {
            addIfNotNull(json, "user", getUser());
            if (getPassword() != null)
                addIfNotNull(json, "password", Base64.encodeBase64String(AES.get().encrypt(getPassword())));
        }
        else
        {
            // if there is no change we still need to pop in the existing password value since the
            // entire JSON object will get rewritten on update
            if (getRowId() != null)
            {
                LabKeyScriptEngineManager svc = LabKeyScriptEngineManager.get();
                ExternalScriptEngineDefinition existingDef = svc.getEngineDefinition(getRowId(), getType());

                if (existingDef != null)
                {
                    addIfNotNull(json, "user", existingDef.getUser());
                    if (existingDef.getPassword() != null)
                        addIfNotNull(json, "password", Base64.encodeBase64String(AES.get().encrypt(existingDef.getPassword())));
                }
            }
        }
        addIfNotNull(json, "external", isExternal());
        addIfNotNull(json, "remote", isRemote());
        addIfNotNull(json, "docker", isDocker());
        addIfNotNull(json, "pandocEnabled", isPandocEnabled());
        addIfNotNull(json, "pathMap", _pathMap);
        addIfNotNull(json, "default", isDefault());
        addIfNotNull(json, "sandboxed", isSandboxed());
        addIfNotNull(json, "dockerImageRowId", getDockerImageRowId());
        addIfNotNull(json, "remoteUrl", getRemoteUrl());

        _configuration = json.toString();
    }

    /**
     * Setter from DB binding
     */
    public void setConfiguration(String configuration) throws IOException
    {
        setConfiguration(configuration, true);
    }

    public void setConfiguration(String configuration, boolean decrypt) throws IOException
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
        {
            String password = json.getString("password");
            if (decrypt)
                setPassword(AES.get().decrypt(Base64.decodeBase64(password)));
            else
                setPassword(password);
        }
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
        if (json.has("sandboxed"))
            setSandboxed(json.getBoolean("sandboxed"));
        if (json.has("dockerImageRowId"))
        {
            int imageRowId = json.getInt("dockerImageRowId");
            if (imageRowId > 0)
            {
                setDockerImageRowId(imageRowId);
                DockerService service = DockerService.get();
                if (service != null && service.isDockerEnabled())
                {
                    DockerService.DockerImage image = DockerService.get().getDockerImage(imageRowId);
                    if (image != null)
                        setDockerImageConfig(image.getConfiguration());
                }
            }
        }
        if (json.has("remoteUrl"))
            setRemoteUrl(json.getString("remoteUrl"));

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

    public boolean isChangePassword()
    {
        return _changePassword;
    }

    public void setChangePassword(boolean changePassword)
    {
        _changePassword = changePassword;
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

    @Override
    public boolean isSandboxed()
    {
        return _sandboxed;
    }

    @Override
    public void setSandboxed(boolean sandboxed)
    {
        _sandboxed = sandboxed;
    }

    public String getRemoteUrl()
    {
        return _remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl)
    {
        _remoteUrl = remoteUrl;
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

    @Override
    public Integer getDockerImageRowId()
    {
        return _dockerImageRowId;
    }

    @Override
    public void setDockerImageRowId(Integer dockerImageRowId)
    {
        _dockerImageRowId = dockerImageRowId;
    }

    @Override
    public String getDockerImageConfig()
    {
        return _dockerImageConfig;
    }

    @Override
    public void setDockerImageConfig(String dockerImageConfig)
    {
        _dockerImageConfig = dockerImageConfig;
    }

    public void saveDockerImageConfig(User user) throws IOException
    {
        DockerService service = DockerService.get();
        if (service != null && service.isDockerEnabled())
        {
            DockerService.DockerImage image = service.getDockerImage(_dockerImageRowId);
            if (image != null)
            {
                setDockerImageRowId(service.saveDockerImage(user, _dockerImageConfig, image.getImageName(), image.getType(), image.getDescription(), image.getRowId()));
            }
            else
            {
                setDockerImageRowId(service.saveDockerImage(user, _dockerImageConfig, "Docker - " + getName(), DOCKER_IMAGE_TYPE, "", null));
            }
        }
    }
}
