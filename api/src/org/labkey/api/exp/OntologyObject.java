/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;

/**
 * User: migra
 * Date: Oct 25, 2005
 * Time: 8:04:48 PM
 */
public class OntologyObject
{
    private long objectId;
    private @NotNull Container container;
    private @NotNull String objectURI;
    private Long ownerObjectId;

    public long getObjectId()
    {
        return objectId;
    }

    public void setObjectId(long objectId)
    {
        assert objectId > 0;
        this.objectId = objectId;
    }

    public @NotNull Container getContainer()
    {
        return container;
    }

    public void setContainer(@NotNull Container container)
    {
        this.container = container;
    }

    public @NotNull String getObjectURI()
    {
        return objectURI;
    }

    public void setObjectURI(@NotNull String objectURI)
    {
        this.objectURI = objectURI;
    }

    public Long getOwnerObjectId()
    {
        return ownerObjectId;
    }

    public void setOwnerObjectId(Long ownerObjectId)
    {
        this.ownerObjectId = ownerObjectId;
    }
}
