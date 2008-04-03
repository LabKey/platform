package org.labkey.biotrue.datamodel;

import java.util.Date;

public class Server
{
    int rowId;
    String name;
    String container;
    String wsdlURL;
    String serviceNamespaceURI;
    String serviceLocalPart;
    String userName;
    String password;
    String physicalRoot;
    int syncInterval;
    Date nextSync;

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int id)
    {
        rowId = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getContainer()
    {
        return container;
    }

    public void setContainer(String container)
    {
        this.container = container;
    }

    public String getWsdlURL()
    {
        return wsdlURL;
    }

    public void setWsdlURL(String url)
    {
        wsdlURL = url;
    }

    public String getServiceNamespaceURI()
    {
        return serviceNamespaceURI;
    }

    public void setServiceNamespaceURI(String uri)
    {
        serviceNamespaceURI = uri;
    }

    public String getServiceLocalPart()
    {
        return serviceLocalPart;
    }

    public void setServiceLocalPart(String part)
    {
        serviceLocalPart = part;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String name)
    {
        userName = name;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getPhysicalRoot()
    {
        return physicalRoot;
    }

    public void setPhysicalRoot(String physicalRoot)
    {
        this.physicalRoot = physicalRoot;
    }

    public int getSyncInterval()
    {
        return syncInterval;
    }

    public void setSyncInterval(int syncInterval)
    {
        this.syncInterval = syncInterval;
    }

    public Date getNextSync()
    {
        return nextSync;
    }

    public void setNextSync(Date nextSync)
    {
        this.nextSync = nextSync;
    }
}
