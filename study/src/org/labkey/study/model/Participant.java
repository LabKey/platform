/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

/**
 * User: brittp
 * Created: Jan 17, 2008 10:26:32 AM
 */
public class Participant
{
    private Container _container;
    private String _participantId;
    private Integer _enrollmentSiteId;      // This is a locationId, but still needs to match the column in the table
    private Integer _currentSiteId;         // This is a locationId, but still needs to match the column in the table
    private Date _startDate;
    private Integer _initialCohortId;
    private Integer _currentCohortId;

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String getParticipantId()
    {
        return _participantId;
    }

    public void setParticipantId(String participantId)
    {
        _participantId = participantId;
    }

    public Integer getEnrollmentSiteId()
    {
        return _enrollmentSiteId;
    }

    public void setEnrollmentSiteId(Integer enrollmentSiteId)
    {
        _enrollmentSiteId = enrollmentSiteId;
    }

    public Integer getCurrentSiteId()
    {
        return _currentSiteId;
    }

    public void setCurrentSiteId(Integer currentSiteId)
    {
        _currentSiteId = currentSiteId;
    }

    public Date getStartDate()
    {
        return _startDate;
    }

    public void setStartDate(Date startDate)
    {
        _startDate = startDate;
    }

    public Integer getCurrentCohortId()
    {
        return _currentCohortId;
    }

    public void setCurrentCohortId(Integer currentCohortId)
    {
        _currentCohortId = currentCohortId;
    }

    public Integer getInitialCohortId()
    {
        return _initialCohortId;
    }

    public void setInitialCohortId(Integer initialCohortId)
    {
        _initialCohortId = initialCohortId;
    }
}
