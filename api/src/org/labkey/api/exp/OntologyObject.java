/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
package org.labkey.api.exp;

import org.labkey.api.data.Container;

/**
 * User: migra
 * Date: Oct 25, 2005
 * Time: 8:04:48 PM
 */
public class OntologyObject
{
    private int objectId;
    private Container container;
    private String objectURI;
    private Integer ownerObjectId;

    public int getObjectId()
    {
        return objectId;
    }

    public void setObjectId(int objectId)
    {
        this.objectId = objectId;
    }

    public Container getContainer()
    {
        return container;
    }

    public void setContainer(Container container)
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
