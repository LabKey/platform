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
    /**
     * @gwt.typeArgs <org.labkey.api.gwt.client.model.GWTPropertyDescriptor>
     */
    private List propertyDescriptors;

    /**
     * @gwt.typeArgs <java.lang.String>
     */
    private Set requiredPropertyDescriptors;

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
        this.propertyDescriptors = new ArrayList();
        this.requiredPropertyDescriptors = new HashSet();
        this.allowFileLinkProperties = src.allowFileLinkProperties;
        this.allowAttachmentProperties = src.allowAttachmentProperties;
        if (src.getPropertyDescriptors() == null)
            return;
        for (int i=0 ; i<src.getPropertyDescriptors().size() ; i++)
            this.propertyDescriptors.add(new GWTPropertyDescriptor((GWTPropertyDescriptor)src.getPropertyDescriptors().get(i)));

        if (src.getRequiredPropertyDescriptors() != null)
        {
            for (Iterator it = src.getRequiredPropertyDescriptors().iterator() ; it.hasNext() ; )
                this.requiredPropertyDescriptors.add(it.next());
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

    public List getPropertyDescriptors()
    {
        return propertyDescriptors;
    }

    public void setPropertyDescriptors(List list)
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

    public Set getRequiredPropertyDescriptors()
    {
        return requiredPropertyDescriptors;
    }

    public void setRequiredPropertyDescriptors(Set requiredPropertyDescriptors)
    {
        this.requiredPropertyDescriptors = requiredPropertyDescriptors;
    }
}
