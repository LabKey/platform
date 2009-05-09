/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.data.Entity;

/*
 * User: brittp
 * Date: Aug 20, 2008
 * Time: 3:31:45 PM
 */

public class SpecimenComment extends Entity
{
    private String _globalUniqueId;
    private String _specimenHash;
    private String _comment;
    private Integer _rowId;
    private boolean _qualityControlFlag;
    private boolean _qualityControlFlagForced;
    private String _qualityControlComments;

    public String getGlobalUniqueId()
    {
        return _globalUniqueId;
    }

    public void setGlobalUniqueId(String globalUniqueId)
    {
        _globalUniqueId = globalUniqueId;
    }

    public String getComment()
    {
        return _comment;
    }

    public void setComment(String comment)
    {
        _comment = comment;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public String getSpecimenHash()
    {
        return _specimenHash;
    }

    public void setSpecimenHash(String specimenHash)
    {
        _specimenHash = specimenHash;
    }

    public boolean isQualityControlFlag()
    {
        return _qualityControlFlag;
    }

    public void setQualityControlFlag(boolean qualityControlFlag)
    {
        _qualityControlFlag = qualityControlFlag;
    }

    public boolean isQualityControlFlagForced()
    {
        return _qualityControlFlagForced;
    }

    public void setQualityControlFlagForced(boolean qualityControlFlagForced)
    {
        _qualityControlFlagForced = qualityControlFlagForced;
    }

    public String getQualityControlComments()
    {
        return _qualityControlComments;
    }

    public void setQualityControlComments(String qualityControlComments)
    {
        _qualityControlComments = qualityControlComments;
    }
}