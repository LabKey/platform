package org.labkey.biotrue.controllers;

import org.labkey.api.view.ViewForm;

public class NewServerForm extends ViewForm
{
    public String ff_name;
    public String ff_wsdlURL;
    public String ff_serviceNamespaceURI;
    public String ff_serviceLocalPart;
    public String ff_username;
    public String ff_password;
    public String ff_physicalRoot;

    public void setFf_name(String ff_name)
    {
        this.ff_name = ff_name;
    }

    public void setFf_wsdlURL(String ff_wsdlURL)
    {
        this.ff_wsdlURL = ff_wsdlURL;
    }

    public void setFf_serviceNamespaceURI(String ff_serviceNamespaceURI)
    {
        this.ff_serviceNamespaceURI = ff_serviceNamespaceURI;
    }

    public void setFf_serviceLocalPart(String ff_serviceLocalPart)
    {
        this.ff_serviceLocalPart = ff_serviceLocalPart;
    }

    public void setFf_username(String ff_username)
    {
        this.ff_username = ff_username;
    }

    public void setFf_password(String ff_password)
    {
        this.ff_password = ff_password;
    }

    public void setFf_physicalRoot(String ff_physicalRoot)
    {
        this.ff_physicalRoot = ff_physicalRoot;
    }
}
