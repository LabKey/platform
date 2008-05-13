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

package org.labkey.api.study.assay;

import java.util.Date;

/**
 * User: brittp
 * Date: Jul 30, 2007
 * Time: 4:29:10 PM
 */
public class AssayPublishKey
{
    private String _participantId;
    private float _visitId;
    private Date _date;
    private Object _dataId;

    public AssayPublishKey(String participantId, float visitId, Object dataId)
    {
        _participantId = participantId;
        _visitId = visitId;
        _dataId = dataId;
    }

    public AssayPublishKey(String participantId, Date date, Object dataId)
    {
        _participantId = participantId;
        _date = date;
        _dataId = dataId;
    }

    public String getParticipantId()
    {
        return _participantId;
    }

    public float getVisitId()
    {
        return _visitId;
    }

    public Object getDataId()
    {
        return _dataId;
    }

    public Date getDate()
    {
        return _date;
    }
}
