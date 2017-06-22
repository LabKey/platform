/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.data.xml.ActionLink;
import org.labkey.data.xml.ActionOptions;
import org.labkey.data.xml.PipelineOptionsDocument;
import org.labkey.data.xml.TbarBtnOptions;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Feb 2, 2010
 * Time: 9:35:36 AM
 */
public class FilesAdminOptions
{
    private boolean _importDataEnabled = true;
    private Boolean _showFolderTree;
    private Boolean _expandFileUpload;
    /** True if we should get our toolbar configuration (the actions available for files) from our parent container */
    private boolean _inheritedTbarConfig = false;
    private Container _container;
    private Map<String, PipelineActionConfig> _pipelineConfig = new HashMap<>();
    private fileConfig _fileConfig = fileConfig.useDefault;
    private Map<String, FilesTbarBtnOption> _tbarConfig = new HashMap<>();
    private static Comparator<FilesTbarBtnOption> TBAR_BTN_COMPARATOR = new TbarButtonComparator();
    private String _gridConfig;

    public enum fileConfig {
        useDefault,
        useCustom,
        useParent,
    }

    public enum configProps {
        actions,
        fileConfig,
        importDataEnabled,
        tbarActions,
        inheritedFileConfig,
        inheritedTbarConfig,
        gridConfig,
        expandFileUpload,
        showFolderTree,
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
            XmlBeansUtil.validateXmlDocument(doc, "FilesAdminOptions for " + _container);

            PipelineOptionsDocument.PipelineOptions pipeOptions = doc.getPipelineOptions();
            if (pipeOptions != null)
            {
                _importDataEnabled = pipeOptions.getImportEnabled();

                if (pipeOptions.isSetExpandFileUpload())
                    _expandFileUpload = pipeOptions.getExpandFileUpload();
                if (pipeOptions.isSetShowFolderTree())
                    _showFolderTree = pipeOptions.getShowFolderTree();

                if (pipeOptions.getFilePropertiesConfig() != null)
                    _fileConfig = fileConfig.valueOf(pipeOptions.getFilePropertiesConfig());

                // Make sure workbooks always defer to their parent
                _inheritedTbarConfig = pipeOptions.getInheritedTbarConfig() || getContainer().isWorkbook();

                ActionOptions actionOptions = pipeOptions.getActionConfig();
                if (actionOptions != null)
                {
                    for (ActionOptions.DisplayOption o : actionOptions.getDisplayOptionArray())
                    {
                        PipelineActionConfig pa = new PipelineActionConfig(o.getId(), o.getState(), o.getLabel());

                        ActionLink links = o.getLinks();
                        if (links != null)
                        {
                            List<PipelineActionConfig> actionLinks = new ArrayList<>();

                            for (ActionLink.DisplayOption lo : links.getDisplayOptionArray())
                                actionLinks.add(new PipelineActionConfig(lo.getId(), lo.getState(), lo.getLabel()));

                            pa.setLinks(actionLinks);
                        }
                        _pipelineConfig.put(pa.getId(), pa);
                    }
                }

                TbarBtnOptions btnOptions = pipeOptions.getTbarConfig();
                if (btnOptions != null)
                {
                    for (TbarBtnOptions.TbarBtnOption o : btnOptions.getTbarBtnOptionArray())
                    {
                        _tbarConfig.put(o.getId(), new FilesTbarBtnOption(o.getId(), o.getPosition(), o.getHideText(), o.getHideIcon()));
                    }
                }

                _gridConfig = pipeOptions.getGridConfig();
            }
        }
        catch (XmlValidationException | XmlException e)
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

    public Boolean getShowFolderTree()
    {
        return _showFolderTree;
    }

    public void setShowFolderTree(Boolean showFolderTree)
    {
        _showFolderTree = showFolderTree;
    }

    public Boolean getExpandFileUpload()
    {
        return _expandFileUpload;
    }

    public void setExpandFileUpload(Boolean expandFileUpload)
    {
        _expandFileUpload = expandFileUpload;
    }


    public boolean isInheritedTbarConfig()
    {
        return _inheritedTbarConfig;
    }

    public void setInheritedTbarConfig(boolean inheritedTbarConfig)
    {
        _inheritedTbarConfig = inheritedTbarConfig;
    }

    public List<PipelineActionConfig> getPipelineConfig()
    {
        return new ArrayList<>(_pipelineConfig.values());
    }

    public void setPipelineConfig(List<PipelineActionConfig> pipelineConfig)
    {
        _pipelineConfig.clear();
        for (PipelineActionConfig config : pipelineConfig)
            _pipelineConfig.put(config.getId(), config);
    }

    public void addDefaultPipelineConfig(PipelineActionConfig config)
    {
        if (!_pipelineConfig.containsKey(config.getId()))
            _pipelineConfig.put(config.getId(), config);
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

    public List<FilesTbarBtnOption> getEffectiveTbarConfig()
    {
        Collection<FilesTbarBtnOption> configs;
        // Workbooks always defer to their parent
        if (!getContainer().isWorkbook() && (!_inheritedTbarConfig || getContainer().isProject() || getContainer().isRoot()))
        {
            configs = _tbarConfig.values();
        }
        else
        {
            // Recurse up to our parent, which may have its own settings or defer further up the chain
            configs = ServiceRegistry.get().getService(FileContentService.class).getAdminOptions(_container.getParent()).getEffectiveTbarConfig();
        }

        List<FilesTbarBtnOption> result = new ArrayList<>(configs);
        result.sort(TBAR_BTN_COMPARATOR);
        return result;
    }

    public void setTbarConfig(List<FilesTbarBtnOption> tbarConfig)
    {
        _tbarConfig.clear();
        for (FilesTbarBtnOption o : tbarConfig)
            _tbarConfig.put(o.getId(), o);
    }

    public String getGridConfig()
    {
        return _gridConfig;
    }

    public void setGridConfig(String gridConfig)
    {
        _gridConfig = gridConfig;
    }

    public JSONObject getInheritedFileConfig()
    {
        JSONObject o = new JSONObject();

        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        Container container = getContainer();
        FilesAdminOptions options = svc.getAdminOptions(container);

        if (options.getFileConfig() == FilesAdminOptions.fileConfig.useParent)
        {
            while (container != container.getProject())
            {
                container = container.getParent();
                options = svc.getAdminOptions(container);

                if (options.getFileConfig() != FilesAdminOptions.fileConfig.useParent)
                {
                    o.put(configProps.fileConfig.name(), options.getFileConfig().name());
                    o.put("containerPath", container.getPath());

                    return o;
                }
            }
            FilesAdminOptions.fileConfig cfg = svc.getAdminOptions(container).getFileConfig();
            if (cfg != FilesAdminOptions.fileConfig.useParent)
                o.put(configProps.fileConfig.name(), cfg.name());
            else
                o.put(configProps.fileConfig.name(), FilesAdminOptions.fileConfig.useDefault.name());

            o.put("containerPath", container.getPath());
        }
        else
        {
            o.put(configProps.fileConfig.name(), options.getFileConfig().name());
            o.put("containerPath", container.getPath());
        }
        return o;
    }

    public String serialize()
    {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            PipelineOptionsDocument doc = PipelineOptionsDocument.Factory.newInstance();
            PipelineOptionsDocument.PipelineOptions pipelineOptions = doc.addNewPipelineOptions();

            pipelineOptions.setImportEnabled(isImportDataEnabled());
            pipelineOptions.setFilePropertiesConfig(_fileConfig.name());
            if (_expandFileUpload != null)
                pipelineOptions.setExpandFileUpload(_expandFileUpload);
            if (_showFolderTree != null)
                pipelineOptions.setShowFolderTree(_showFolderTree);

            if (!_pipelineConfig.isEmpty())
            {
                ActionOptions actionOptions = pipelineOptions.addNewActionConfig();

                for (PipelineActionConfig ac : _pipelineConfig.values())
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

            pipelineOptions.setInheritedTbarConfig(_inheritedTbarConfig);

            if (!_tbarConfig.isEmpty())
            {
                TbarBtnOptions tbarOptions = pipelineOptions.addNewTbarConfig();

                for (FilesTbarBtnOption o : _tbarConfig.values())
                {
                    TbarBtnOptions.TbarBtnOption tbarOption = tbarOptions.addNewTbarBtnOption();

                    tbarOption.setId(o.getId());
                    tbarOption.setPosition(o.getPosition());
                    tbarOption.setHideText(o.isHideText());
                    tbarOption.setHideIcon(o.isHideIcon());
                }
            }

            if (_gridConfig != null)
                pipelineOptions.setGridConfig(_gridConfig);

            XmlBeansUtil.validateXmlDocument(doc, "FilesAdminOptions for " + _container);
            doc.save(output, XmlBeansUtil.getDefaultSaveOptions());

            return output.toString();
        }
        catch (Exception e)
        {
            // This is likely a code problem -- propagate it up so we log to mothership
            throw new RuntimeException(e);
        }
    }

    public void updateFromJSON(Map<String, Object> props)
    {
        if (props.containsKey(configProps.actions.name()))
        {
            Object actions = props.get(configProps.actions.name());
            if (actions instanceof JSONArray)
            {
                JSONArray jarray = (JSONArray)actions;

                for (int i=0; i < jarray.length(); i++)
                {
                    PipelineActionConfig config = PipelineActionConfig.fromJSON(jarray.getJSONObject(i));
                    if (config != null)
                    {
                        if (_pipelineConfig.containsKey(config.getId()))
                            _pipelineConfig.get(config.getId()).update(config);
                        else
                            _pipelineConfig.put(config.getId(), config);
                    }
                }
            }
        }
        if (props.containsKey(configProps.importDataEnabled.name()))
            setImportDataEnabled((Boolean)props.get(configProps.importDataEnabled.name()));

        if (props.containsKey(configProps.fileConfig.name()))
            setFileConfig(fileConfig.valueOf((String) props.get(configProps.fileConfig.name())));

        if (props.containsKey(configProps.inheritedTbarConfig.name()))
            setInheritedTbarConfig((Boolean) props.get(configProps.inheritedTbarConfig.name()));

        if (props.containsKey(configProps.expandFileUpload.name()))
            setExpandFileUpload((Boolean)props.get(configProps.expandFileUpload.name()));

        if (props.containsKey(configProps.showFolderTree.name()))
            setShowFolderTree((Boolean)props.get(configProps.showFolderTree.name()));

        if (props.containsKey(configProps.tbarActions.name()))
        {
            Object actions = props.get(configProps.tbarActions.name());
            if (actions instanceof JSONArray)
            {
                JSONArray jarray = (JSONArray)actions;
                _tbarConfig.clear();

                for (int i=0; i < jarray.length(); i++)
                {
                    FilesTbarBtnOption o = FilesTbarBtnOption.fromJSON(jarray.getJSONObject(i));
                    if (o != null)
                        _tbarConfig.put(o.getId(), o);
                }
            }
        }

        if (props.containsKey(configProps.gridConfig.name()))
            _gridConfig = String.valueOf(props.get(configProps.gridConfig.name()));
    }

    public static FilesAdminOptions fromJSON(Container c, Map<String,Object> props)
    {
        FilesAdminOptions options = new FilesAdminOptions(c);

        options.updateFromJSON(props);
        return options;
    }

    public Map<String, Object> toJSON()
    {
        Map<String, Object> props = new HashMap<>();

        if (!_pipelineConfig.isEmpty())
        {
            JSONArray actions = new JSONArray();

            for (PipelineActionConfig config : getPipelineConfig())
            {
                actions.put(config.toJSON());
            }
            props.put(configProps.actions.name(), actions);
        }
        props.put(configProps.importDataEnabled.name(), isImportDataEnabled());
        props.put(configProps.fileConfig.name(), _fileConfig.name());
        props.put(configProps.inheritedFileConfig.name(), getInheritedFileConfig());
        if (_expandFileUpload != null)
            props.put(configProps.expandFileUpload.name(), _expandFileUpload);
        if (_showFolderTree != null)
            props.put(configProps.showFolderTree.name(), _showFolderTree);

        props.put(configProps.inheritedTbarConfig.name(), _inheritedTbarConfig);

        List<FilesTbarBtnOption> effectiveTbarConfig = getEffectiveTbarConfig();
        if (!effectiveTbarConfig.isEmpty())
        {
            JSONArray tbarOptions = new JSONArray();

            for (FilesTbarBtnOption o : effectiveTbarConfig)
            {
                tbarOptions.put(o.toJSON());
            }
            props.put(configProps.tbarActions.name(), tbarOptions);
        }

        if (_gridConfig != null)
            props.put(configProps.gridConfig.name(), new JSONObject(_gridConfig));

        return props;
    }

    private static class TbarButtonComparator implements Comparator<FilesTbarBtnOption>
    {
        @Override
        public int compare(FilesTbarBtnOption o1, FilesTbarBtnOption o2)
        {
            return o1.getPosition() - o2.getPosition();
        }
    }
}
