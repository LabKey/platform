package org.labkey.api.exp;

/**
 * Copyright (C) 2004 Fred Hutchinson Cancer Research Center. All Rights Reserved.
 * User: migra
 * Date: Oct 25, 2005
 * Time: 8:04:48 PM
 */
public class OntologyObject
{
    private int ObjectId;
    private String container;
    private String objectURI;
    private Integer ownerObjectId;

    public int getObjectId()
    {
        return ObjectId;
    }

    public void setObjectId(int objectId)
    {
        ObjectId = objectId;
    }

    public String getContainer()
    {
        return container;
    }

    public void setContainer(String container)
    {
        this.container = container;
    }

    public String getObjectURI()
    {
        return objectURI;
    }

    public void setObjectURI(String objectURI)
    {
        this.objectURI = objectURI;
    }

    public Integer getOwnerObjectId()
    {
        return ownerObjectId;
    }

    public void setOwnerObjectId(Integer ownerObjectId)
    {
        this.ownerObjectId = ownerObjectId;
    }
}
