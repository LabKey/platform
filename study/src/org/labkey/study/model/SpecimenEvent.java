/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.study.model;

import org.labkey.api.data.Container;

import java.util.Date;
import java.util.Map;

/**
 * User: brittp
 * Date: Mar 15, 2006
 * Time: 4:35:28 PM
 */
public class SpecimenEvent extends AbstractStudyCachable<SpecimenEvent>
{
    private final Map _rowMap;
    private final Container _container;

    public SpecimenEvent(Container container, Map rowMap)
    {
        _container = container;
        _rowMap = rowMap;
    }

    public Object get(String key)
    {
        return _rowMap.get(key);
    }

    public String getComments()
    {
        return (String)get("comments");
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        throw new IllegalStateException("Container should be set in constructor");
    }

    public Integer getLabId()
    {
        return (Integer)get("labid");
    }

    public Date getLabReceiptDate()
    {
        return (Date)get("labreceiptdate");
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public long getRowId()
    {
        Long id = (Long)get("rowid");
        if (null != id)
            return id;
        return 0;
    }

    public long getExternalId()
    {
        Long id = (Long)get("externalid");
        if (null != id)
            return id;
        return 0;
    }

    public Integer getShipBatchNumber()
    {
        return (Integer)get("shipbatchnumber");
    }

    public Date getShipDate()
    {
        return (Date)get("shipdate");
    }

    public Integer getShipFlag()
    {
        return (Integer)get("shipflag");
    }

    public long getVialId()
    {
        Long id = (Long)get("vialid");
        if (null != id)
            return id;
        return 0;
    }

    public Date getStorageDate()
    {
        return (Date)get("storagedate");
    }

    public Integer getOriginatingLocationId()
    {
        return (Integer)get("originatinglocationid");
    }

    public String getProcessedByInitials()
    {
        return (String)get("processedbyinitials");
    }

    public String getQualityComments()
    {
        return (String)get("qualitycomments");
    }

    public boolean getObsolete()
    {
        return (boolean)get("obsolete");
    }
}
