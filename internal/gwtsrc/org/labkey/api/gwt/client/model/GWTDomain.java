/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 24, 2007
 * Time: 1:44:49 PM
 */
public class GWTDomain implements IsSerializable
{
    private int domainId;
    private String name;
    private String domainURI;
    private String description;
    private boolean allowFileLinkProperties;
    private boolean allowAttachmentProperties;
    private List<GWTPropertyDescriptor> propertyDescriptors = new ArrayList<GWTPropertyDescriptor>();

    private Set<String> mandatoryPropertyDescriptorNames = new HashSet<String>();

    private Set<String> reservedPropertyNames = new HashSet<String>();

    public GWTDomain()
    {
    }

    // deep clone constructor
    public GWTDomain(GWTDomain src)
    {
        this.domainId = src.domainId;    
        this.name = src.name;
        this.domainURI = src.domainURI;
        this.description = src.description;
        this.allowFileLinkProperties = src.allowFileLinkProperties;
        this.allowAttachmentProperties = src.allowAttachmentProperties;
        if (src.getPropertyDescriptors() == null)
            return;
        for (int i=0 ; i<src.getPropertyDescriptors().size() ; i++)
            this.propertyDescriptors.add(new GWTPropertyDescriptor((GWTPropertyDescriptor)src.getPropertyDescriptors().get(i)));

        if (src.getMandatoryPropertyDescriptorNames() != null)
        {
            this.mandatoryPropertyDescriptorNames.addAll(src.getMandatoryPropertyDescriptorNames());
        }

        if (src.getReservedPropertyNames() != null)
        {
            this.getReservedPropertyNames().addAll(src.getReservedPropertyNames());
        }
    }


    public int getDomainId()
    {
        return domainId;
    }

    public void setDomainId(int domainId)
    {
        this.domainId = domainId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDomainURI()
    {
        return domainURI;
    }

    public void setDomainURI(String domainURI)
    {
        this.domainURI = domainURI;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public List<GWTPropertyDescriptor> getPropertyDescriptors()
    {
        return propertyDescriptors;
    }

    public void setPropertyDescriptors(List<GWTPropertyDescriptor> list)
    {
        propertyDescriptors = list;
    }

    public boolean isAllowFileLinkProperties()
    {
        return allowFileLinkProperties;
    }

    public void setAllowFileLinkProperties(boolean allowFileLinkProperties)
    {
        this.allowFileLinkProperties = allowFileLinkProperties;
    }

    public boolean isAllowAttachmentProperties()
    {
        return allowAttachmentProperties;
    }

    public void setAllowAttachmentProperties(boolean allowAttachmentProperties)
    {
        this.allowAttachmentProperties = allowAttachmentProperties;
    }

    /**
     * @return names of property descriptors that must be present in this domain. Does not indicate that they must be non-nullable.
     */
    public Set<String> getMandatoryPropertyDescriptorNames()
    {
        return mandatoryPropertyDescriptorNames;
    }

    /**
     * @param mandatoryPropertyDescriptors names of property descriptors that must be present in this domain.  Does not indicate that they must be non-nullable.
     */
    public void setMandatoryPropertyDescriptorNames(Set<String> mandatoryPropertyDescriptors)
    {
        this.mandatoryPropertyDescriptorNames = mandatoryPropertyDescriptors;
    }

    public Set<String> getReservedPropertyNames()
    {
        return reservedPropertyNames;
    }

    public void setReservedPropertyNames(Set<String> reservedPropertyNames)
    {
        this.reservedPropertyNames = reservedPropertyNames;
    }
}
