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

package org.labkey.api.gwt.client.assay.model;

import com.google.gwt.user.client.rpc.IsSerializable;
import org.labkey.api.gwt.client.model.GWTContainer;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Jun 20, 2007
 * Time: 2:29:22 PM
 */
public class GWTProtocol implements IsSerializable
{
    private Integer _protocolId;
    private String _name;
    private String _description;
    private String _providerName;
    private String domainKindName;

    private Map<String, String> _protocolParameters;

    private List<GWTDomain<GWTPropertyDescriptor>> _domains;

    private List<String> _availablePlateTemplates;
    private Map<String, String> _availableMetadataInputFormats = new HashMap<String, String>();
    private String _metadataInputFormatHelp;

    private String _selectedPlateTemplate;
    private String _selectedMetadataInputFormat;

    /** Scripts defined in the module itself, associated with the assay provider */
    private List<String> _moduleTransformScripts = new ArrayList<String>();
    /** Scripts defined in the assay definition */
    private List<String> _protocolTransformScripts = new ArrayList<String>();

    private List<String> _availableDetectionMethods;
    private String _selectedDetectionMethod;

    private boolean _allowBackgroundUpload;
    private boolean _allowEditableResults;
    private boolean _allowQCStates;
    private boolean _allowSpacesInPath;
    private boolean _allowTransformationScript;
    private boolean _allowPlateMetadata;

    // UNDONE: update 'autoCopy' to 'autoLink' for the two members below and align ui-components and tests accordingly
    private GWTContainer _autoCopyTargetContainer;
    private String _autoCopyTargetContainerId;
    private String _autoLinkCategory;
    private boolean _saveScriptFiles;
    private boolean _editableRuns;
    private boolean _editableResults;
    private boolean _backgroundUpload;
    private boolean _qcEnabled;
    private boolean _plateMetadata;
    private String _status;

    public GWTProtocol()
    {
    }

    public Integer getProtocolId()
    {
        return _protocolId;
    }

    public void setProtocolId(Integer protocolId)
    {
        _protocolId = protocolId;
    }


    public List<GWTDomain<GWTPropertyDescriptor>> getDomains()
    {
        return _domains;
    }

    public void setDomains(List<GWTDomain<GWTPropertyDescriptor>> domains)
    {
        _domains = domains;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public Map<String, String> getProtocolParameters()
    {
        return _protocolParameters;
    }

    public void setProtocolParameters(Map<String, String> protocolParameters)
    {
        _protocolParameters = protocolParameters;
    }

    public String getProviderName()
    {
        return _providerName;
    }

    public void setProviderName(String providerName)
    {
        _providerName = providerName;
    }

    public String getDomainKindName()
    {
        return domainKindName;
    }

    public void setDomainKindName(String domainKindName)
    {
        this.domainKindName = domainKindName;
    }

    public List<String> getAvailablePlateTemplates()
    {
        return _availablePlateTemplates;
    }

    public void setAvailablePlateTemplates(List<String> availablePlateTemplates)
    {
        _availablePlateTemplates = availablePlateTemplates;
    }

    public String getSelectedPlateTemplate()
    {
        return _selectedPlateTemplate;
    }

    public void setSelectedPlateTemplate(String selectedPlateTemplate)
    {
        _selectedPlateTemplate = selectedPlateTemplate;
    }

    public List<String> getModuleTransformScripts()
    {
        return _moduleTransformScripts;
    }

    public void setModuleTransformScripts(List<String> moduleTransformScripts)
    {
        _moduleTransformScripts = moduleTransformScripts;
    }

    public boolean isAllowTransformationScript()
    {
        return _allowTransformationScript;
    }

    public void setAllowTransformationScript(boolean allowTransformationScript)
    {
        _allowTransformationScript = allowTransformationScript;
    }

    public List<String> getProtocolTransformScripts()
    {
        return _protocolTransformScripts;
    }

    public void setProtocolTransformScripts(List<String> protocolTransformScripts)
    {
        _protocolTransformScripts = new ArrayList<String>(protocolTransformScripts.size());
        for (String script : protocolTransformScripts)
        {
            _protocolTransformScripts.add(script.trim());
        }
    }

    public List<String> getAvailableDetectionMethods()
    {
        return _availableDetectionMethods;
    }

    public void setAvailableDetectionMethods(List<String> availableDetectionMethods)
    {
        _availableDetectionMethods = availableDetectionMethods;
    }

    public String getSelectedDetectionMethod()
    {
        return _selectedDetectionMethod;
    }

    public void setSelectedDetectionMethod(String detectionMethod)
    {
        _selectedDetectionMethod = detectionMethod;
    }

    public GWTContainer getAutoCopyTargetContainer()
    {
        return _autoCopyTargetContainer;
    }

    public void setAutoCopyTargetContainer(GWTContainer autoCopyTargetContainer)
    {
        _autoCopyTargetContainer = autoCopyTargetContainer;
    }

    public String getAutoCopyTargetContainerId()
    {
        return _autoCopyTargetContainerId;
    }

    public void setAutoCopyTargetContainerId(String autoCopyTargetContainerId)
    {
        _autoCopyTargetContainerId = autoCopyTargetContainerId;
    }

    public String getAutoLinkCategory()
    {
        return _autoLinkCategory;
    }

    public void setAutoLinkCategory(String autoLinkCategory)
    {
        _autoLinkCategory = autoLinkCategory;
    }

    public boolean isSaveScriptFiles()
    {
        return _saveScriptFiles;
    }

    public void setSaveScriptFiles(boolean saveScriptFiles)
    {
        _saveScriptFiles = saveScriptFiles;
    }

    public boolean isEditableRuns()
    {
        return _editableRuns;
    }

    public void setEditableRuns(boolean editableRuns)
    {
        _editableRuns = editableRuns;
    }

    public boolean isEditableResults()
    {
        return _editableResults;
    }

    public void setEditableResults(boolean editableResults)
    {
        _editableResults = editableResults;
    }

    public boolean isBackgroundUpload()
    {
        return _backgroundUpload;
    }

    public void setBackgroundUpload(boolean backgroundUpload)
    {
        _backgroundUpload = backgroundUpload;
    }

    public Map<String, String> getAvailableMetadataInputFormats()
    {
        return _availableMetadataInputFormats;
    }

    public void setAvailableMetadataInputFormats(Map<String, String> availableMetadataInputFormats)
    {
        _availableMetadataInputFormats = availableMetadataInputFormats;
    }

    public String getMetadataInputFormatHelp()
    {
        return _metadataInputFormatHelp;
    }

    public void setMetadataInputFormatHelp(String metadataInputFormatHelp)
    {
        _metadataInputFormatHelp = metadataInputFormatHelp;
    }

    public String getSelectedMetadataInputFormat()
    {
        return _selectedMetadataInputFormat;
    }

    public void setSelectedMetadataInputFormat(String selectedMetadataInputFormat)
    {
        _selectedMetadataInputFormat = selectedMetadataInputFormat;
    }

    public boolean isQcEnabled()
    {
        return _qcEnabled;
    }

    public void setQcEnabled(boolean qcEnabled)
    {
        _qcEnabled = qcEnabled;
    }

    public boolean isAllowSpacesInPath()
    {
        return _allowSpacesInPath;
    }

    public void setAllowSpacesInPath(boolean allowSpacesInPath)
    {
        _allowSpacesInPath = allowSpacesInPath;
    }

    public boolean isAllowQCStates()
    {
        return _allowQCStates;
    }

    public void setAllowQCStates(boolean allowQCStates)
    {
        _allowQCStates = allowQCStates;
    }

    public boolean isAllowEditableResults()
    {
        return _allowEditableResults;
    }

    public void setAllowEditableResults(boolean allowEditableResults)
    {
        _allowEditableResults = allowEditableResults;
    }

    public boolean isAllowBackgroundUpload()
    {
        return _allowBackgroundUpload;
    }

    public void setAllowBackgroundUpload(boolean allowBackgroundUpload)
    {
        _allowBackgroundUpload = allowBackgroundUpload;
    }

    public boolean isAllowPlateMetadata()
    {
        return _allowPlateMetadata;
    }

    public void setAllowPlateMetadata(boolean allowPlateMetadata)
    {
        _allowPlateMetadata = allowPlateMetadata;
    }

    public boolean isPlateMetadata()
    {
        return _plateMetadata;
    }

    public void setPlateMetadata(boolean plateMetadata)
    {
        _plateMetadata = plateMetadata;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }
}
