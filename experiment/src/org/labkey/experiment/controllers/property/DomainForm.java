/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.view.ViewForm;

import java.util.LinkedHashMap;
import java.util.Map;

public class DomainForm extends ViewForm
{
    private Domain _domain;
    private String _schemaName;
    private String _queryName;
    private String _domainURI;
    private Integer _domainId;
    private boolean _createOrEdit = false;

    public int getDomainId()
    {
        return -1;
    }

    public void setDomainId(int domainId)
    {
        _domainId = domainId;
    }

    public String getDomainURI()
    {
        return _domainURI;
    }

    public void setDomainURI(String domainURI)
    {
        _domainURI = domainURI;
    }

    public Domain getDomain()
    {
        if (_domain == null)
        {
            if (null != _domainId)
            {
                Domain d = PropertyService.get().getDomain(_domainId);
                if (null != d && d.getContainer().equals(getContainer()))
                    _domain = d;
            }
            else
            {
                String domainURI = _domainURI;
                if (domainURI == null && _schemaName != null && _queryName != null)
                    domainURI = PropertyService.get().getDomainURI(_schemaName, _queryName, getContainer(), getUser());

                if (domainURI != null)
                    _domain = PropertyService.get().getDomain(getContainer(), domainURI);
            }
        }

        return _domain;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
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

    public boolean isCreateOrEdit()
    {
        return _createOrEdit;
    }

    public void setCreateOrEdit(boolean b)
    {
        _createOrEdit = b;
    }
}
