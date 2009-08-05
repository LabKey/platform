/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.experiment.controllers.property;

import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewForm;

import javax.servlet.ServletException;
import java.util.LinkedHashMap;
import java.util.Map;

public class DomainForm extends ViewForm
{
    private Domain _domain;
    private boolean _allowFileLinkProperties = false;
    private boolean _allowAttachmentProperties = false;
    private boolean _showDefaultValueSettings = false;

    public void requiresPermission(int perm) throws ServletException
    {
        if (!getContainer().hasPermission(getUser(), perm))
            HttpView.throwUnauthorized();

        if (_domain != null)
        {
            if (!_domain.getContainer().hasPermission(getUser(), perm))
                HttpView.throwUnauthorized();
        }
    }

    public int getDomainId()
    {
        return -1;
    }

    public void setDomainId(int domainId)
    {
        _domain = PropertyService.get().getDomain(domainId);
    }

    public Domain getDomain()
    {
        return _domain;
    }

    public ActionURL urlFor(Enum action)
    {
        ActionURL ret = getContainer().urlFor(action);
        if (_domain != null)
        {
            ret.addParameter("domainId", Integer.toString(_domain.getTypeId()));
        }
        return ret;
    }

    public ActionURL urlFor(Enum action, DomainProperty pd)
    {
        ActionURL ret = urlFor(action);
        if (pd == null)
        {
            ret.deleteParameter("propertyId");
        }
        else
        {
            ret.replaceParameter("propertyId", Integer.toString(pd.getPropertyId()));
        }
        return ret;
    }

    public String getLabel(DomainProperty pd)
    {
        if (pd == null || pd.getName() == null)
        {
            return "New Column";
        }
        return "Column '" + pd.getName() + "'";
    }
    
    public String typeURItoString(String typeURI)
    {
        PropertyType pt = PropertyType.getFromURI(null, typeURI);
        if (pt.getTypeUri().equals(typeURI))
        {
            return pt.getXmlName();
        }
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(typeURI, getContainer());
        if (dd != null)
        {
            Lsid lsid = new Lsid(dd.getDomainURI());
            if (lsid.getNamespacePrefix().equals("SampleSet"))
            {
                String label = "Lookup: " + dd.getName();
                if (!dd.getContainer().equals(getContainer()))
                {
                    label += " (" + dd.getContainer().getPath() + ")";
                }
                return label;
            }
            return dd.getName();
        }
        return typeURI;
    }

    public boolean allowDomainAsRangeURI(Domain dd)
    {
        return false;
        //return dd.getTypeKind() != null;
    }

    public Map<String, String> getTypeOptions(String currentValue)
    {
        LinkedHashMap<String, String> ret = new LinkedHashMap<String, String>();
        for (PropertyType pt : new PropertyType[] {
                PropertyType.STRING,
                PropertyType.MULTI_LINE,
                PropertyType.DOUBLE,
                PropertyType.INTEGER,
                PropertyType.BOOLEAN,
                PropertyType.DATE_TIME,
                PropertyType.FILE_LINK,
                PropertyType.ATTACHMENT
            })
        {
            ret.put(pt.getTypeUri(), typeURItoString(pt.getTypeUri()));
        }
        Domain[] dds = PropertyService.get().getDomains(getContainer());
        for (Domain dd : dds)
        {
            if (allowDomainAsRangeURI(dd))
            {
                ret.put(dd.getTypeURI(), dd.getLabel(getContainer()));
            }
        }
        if (currentValue != null && !ret.containsKey(currentValue))
        {
            ret.put(currentValue, typeURItoString(currentValue));
        }
        return ret;
    }

    public boolean getAllowFileLinkProperties()
    {
        return _allowFileLinkProperties;
    }

    public void setAllowFileLinkProperties(boolean allowFileLinkProperties)
    {
        _allowFileLinkProperties = allowFileLinkProperties;
    }

    public boolean getAllowAttachmentProperties()
    {
        return _allowAttachmentProperties;
    }

    public void setAllowAttachmentProperties(boolean allowAttachmentProperties)
    {
        _allowAttachmentProperties = allowAttachmentProperties;
    }

    public boolean isShowDefaultValueSettings()
    {
        return _showDefaultValueSettings;
    }

    public void setShowDefaultValueSettings(boolean showDefaultValueSettings)
    {
        _showDefaultValueSettings = showDefaultValueSettings;
    }
}
