/*
 * Copyright (c) 2008 LabKey Corporation
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

import java.util.List;
import java.util.Map;

import org.labkey.api.gwt.client.model.GWTDomain;

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

    private Map<String, String> _protocolParameters;

    private List<GWTDomain> _domains;

    private List<String> _availablePlateTemplates;

    private String _selectedPlateTemplate;

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


    public List<GWTDomain> getDomains()
    {
        return _domains;
    }

    public void setDomains(List<GWTDomain> domains)
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
}
