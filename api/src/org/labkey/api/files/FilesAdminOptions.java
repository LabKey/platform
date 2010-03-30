/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.files;

import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.json.JSONArray;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.data.xml.ActionLink;
import org.labkey.data.xml.ActionOptions;
import org.labkey.data.xml.PipelineOptionsDocument;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Feb 2, 2010
 * Time: 9:35:36 AM
 */
public class FilesAdminOptions
{
    private boolean _importDataEnabled = true;
    private List<PipelineActionConfig> _pipelineConfig = new ArrayList<PipelineActionConfig>();
    private Container _container;
    private Map<String, PipelineActionConfig> _configMap = new HashMap<String, PipelineActionConfig>();
    private fileConfig _fileConfig = fileConfig.useDefault;

    public enum fileConfig {
        useDefault,
        useCustom,
        useParent,
    }

    public FilesAdminOptions(Container c, String xml)
    {
        _container = c;
        if (xml != null)
            _init(xml);
    }

    public FilesAdminOptions(Container c)
    {
        this(c, null);
    }

    private void _init(String xml)
    {
        try {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();

            PipelineOptionsDocument doc = PipelineOptionsDocument.Factory.parse(xml, options);
            XmlBeansUtil.validateXmlDocument(doc);

            PipelineOptionsDocument.PipelineOptions pipeOptions = doc.getPipelineOptions();
            if (pipeOptions != null)
            {
                _importDataEnabled = pipeOptions.getImportEnabled();

                if (pipeOptions.getFilePropertiesConfig() != null)
                    _fileConfig = fileConfig.valueOf(pipeOptions.getFilePropertiesConfig());

                ActionOptions actionOptions = pipeOptions.getActionConfig();
                if (actionOptions != null)
                {
                    for (ActionOptions.DisplayOption o : actionOptions.getDisplayOptionArray())
                    {
                        PipelineActionConfig pa = new PipelineActionConfig(o.getId(), o.getState(), o.getLabel());

                        ActionLink links = o.getLinks();
                        if (links != null)
                        {
                            List<PipelineActionConfig> actionLinks = new ArrayList<PipelineActionConfig>();

                            for (ActionLink.DisplayOption lo : links.getDisplayOptionArray())
                                actionLinks.add(new PipelineActionConfig(lo.getId(), lo.getState(), lo.getLabel()));

                            pa.setLinks(actionLinks);
                        }
                        _pipelineConfig.add(pa);
                    }
                }
            }
        }
        catch (XmlValidationException e)
        {
            throw new RuntimeException(e);
        }
        catch (XmlException e)
        {
            throw new RuntimeException(e);
        }
    }

    public boolean isImportDataEnabled()
    {
        return _importDataEnabled;
    }

    public void setImportDataEnabled(boolean importDataEnabled)
    {
        _importDataEnabled = importDataEnabled;
    }

    public List<PipelineActionConfig> getPipelineConfig()
    {
        return _pipelineConfig;
    }

    public void setPipelineConfig(List<PipelineActionConfig> pipelineConfig)
    {
        _pipelineConfig = pipelineConfig;
    }

    public void addDefaultPipelineConfig(PipelineActionConfig config)
    {
        Map<String, PipelineActionConfig> configMap = getConfigMap();

        if (!configMap.containsKey(config.getId()))
            _pipelineConfig.add(config);
    }

    private Map<String, PipelineActionConfig> getConfigMap()
    {
        if (_configMap.isEmpty())
        {
            for (PipelineActionConfig config : getPipelineConfig())
                _configMap.put(config.getId(), config);
        }
        return _configMap;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public fileConfig getFileConfig()
    {
        return _fileConfig;
    }

    public void setFileConfig(fileConfig fileConfig)
    {
        _fileConfig = fileConfig;
    }

    public String serialize()
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            PipelineOptionsDocument doc = PipelineOptionsDocument.Factory.newInstance();
            PipelineOptionsDocument.PipelineOptions pipelineOptions = doc.addNewPipelineOptions();

            pipelineOptions.setImportEnabled(isImportDataEnabled());
            pipelineOptions.setFilePropertiesConfig(_fileConfig.name());
            
            if (!_pipelineConfig.isEmpty())
            {
                ActionOptions actionOptions = pipelineOptions.addNewActionConfig();

                for (PipelineActionConfig ac : _pipelineConfig)
                {
                    ActionOptions.DisplayOption displayOption = actionOptions.addNewDisplayOption();

                    displayOption.setId(ac.getId());
                    displayOption.setState(ac.getState().name());
                    displayOption.setLabel(ac.getLabel());

                    ActionLink link = displayOption.addNewLinks();
                    for (PipelineActionConfig lac : ac.getLinks())
                    {
                        ActionLink.DisplayOption linkOption = link.addNewDisplayOption();

                        linkOption.setId(lac.getId());
                        linkOption.setState(lac.getState().name());
                        linkOption.setLabel(lac.getLabel());
                    }
                }
            }
            XmlBeansUtil.validateXmlDocument(doc);
            doc.save(output, XmlBeansUtil.getDefaultSaveOptions());

            return output.toString();
        }
        catch (Exception e)
        {
            // This is likely a code problem -- propagate it up so we log to mothership
            throw new RuntimeException(e);
        }
        finally
        {
            IOUtils.closeQuietly(output);
        }
    }

    public static FilesAdminOptions fromJSON(Container c, Map<String,Object> props)
    {
        FilesAdminOptions options = new FilesAdminOptions(c);

        if (props.containsKey("actions"))
        {
            Object actions = props.get("actions");
            if (actions instanceof JSONArray)
            {
                List<PipelineActionConfig> configs = new ArrayList<PipelineActionConfig>();
                JSONArray jarray = (JSONArray)actions;

                for (int i=0; i < jarray.length(); i++)
                {
                    PipelineActionConfig config = PipelineActionConfig.fromJSON(jarray.getJSONObject(i));
                    if (config != null)
                        configs.add(config);
                }
                options.setPipelineConfig(configs);
            }
        }
        if (props.containsKey("importDataEnabled"))
            options.setImportDataEnabled((Boolean)props.get("importDataEnabled"));

        if (props.containsKey("fileConfig"))
            options.setFileConfig(fileConfig.valueOf((String)props.get("fileConfig")));

        return options;
    }

    public Map<String, Object> toJSON()
    {
        Map<String, Object> props = new HashMap<String, Object>();

        if (!_pipelineConfig.isEmpty())
        {
            JSONArray actions = new JSONArray();

            for (PipelineActionConfig config : _pipelineConfig)
            {
                actions.put(config.toJSON());
            }
            props.put("actions", actions);
        }
        props.put("importDataEnabled", isImportDataEnabled());
        props.put("fileConfig", _fileConfig.name());
        
        return props;
    }
}
