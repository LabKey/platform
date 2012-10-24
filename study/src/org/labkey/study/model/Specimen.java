/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
import org.labkey.api.study.StudyService;

import java.util.Date;

/**
 * User: brittp
 * Date: Mar 15, 2006
 * Time: 4:26:07 PM
 */
public class Specimen extends AbstractStudyCachable<Specimen>
{
    private int _rowId; // INT NOT NULL, -- FK exp.Material
    private int _specimenId; // INT NOT NULL, -- FK exp.Material
    private Container _container; // ENTITYID NOT NULL,
    private String _globalUniqueId; // NVARCHAR(20) NOT NULL,
    private String _ptid; // NVARCHAR(32),
    private Date _drawTimestamp; // DATETIME,
    private Date _salReceiptDate; // DATETIME,
    private String _classId; // NVARCHAR(4),
    private Double _visitValue; // FLOAT,
    private String _protocolNumber; // NVARCHAR(10),
    private String _visitDescription; // NVARCHAR(3),
    private Float _volume; // FLOAT,
    private String _volumeUnits; // NVARCHAR(3),
    private String _subAdditiveDerivative; // NVARCHAR(20),
    private Integer _primaryTypeId; // INT,
    private Integer _derivativeTypeId; // INT,
    private Integer _derivativeTypeId2; // INT,
    private Integer _additiveTypeId; // INT,
    private Integer _originatingLocationId;
    private Integer _processingLocation;
    private Integer _currentLocation;
    private String _specimenHash;
    private Date _frozenTime;
    private Date _processingTime;
    private Float _primaryVolume;
    private String _primaryVolumeUnits;
    private boolean _atRepository = false;
    private boolean _available = false;
    private boolean _lockedInRequest = false;
    private Boolean _requestable;
    private String _firstProcessedByInitials;
    private String _availabilityReason;
    private String _latestDeviationCode1;
    private String _latestDeviationCode2;
    private String _latestDeviationCode3;
    private String _latestComments;
    private String _latestQualityComments;
    private Float _latestYield;
    private Float _latestConcentration;
    private Float _latestRatio;
    private Float _latestIntegrity;

    public Integer getAdditiveTypeId()
    {
        return _additiveTypeId;
    }

    public int getSpecimenId()
    {
        return _specimenId;
    }

    public void setSpecimenId(int specimenId)
    {
        _specimenId = specimenId;
    }

    public void setAdditiveTypeId(Integer additiveTypeId)
    {
        verifyMutability();
        _additiveTypeId = additiveTypeId;
    }

    public String getClassId()
    {
        return _classId;
    }

    public void setClassId(String classId)
    {
        verifyMutability();
        _classId = classId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        verifyMutability();
        _container = container;
    }

    public Integer getDerivativeTypeId()
    {
        return _derivativeTypeId;
    }

    public void setDerivativeTypeId(Integer derivativeTypeId)
    {
        verifyMutability();
        _derivativeTypeId = derivativeTypeId;
    }

    public Date getDrawTimestamp()
    {
        return _drawTimestamp;
    }

    public void setDrawTimestamp(Date drawTimestamp)
    {
        verifyMutability();
        _drawTimestamp = drawTimestamp;
    }

    public String getGlobalUniqueId()
    {
        return _globalUniqueId;
    }

    public void setGlobalUniqueId(String globalUniqueId)
    {
        verifyMutability();
        _globalUniqueId = globalUniqueId;
    }

    public Integer getPrimaryTypeId()
    {
        return _primaryTypeId;
    }

    public void setPrimaryTypeId(Integer primaryTypeId)
    {
        verifyMutability();
        _primaryTypeId = primaryTypeId;
    }

    public String getProtocolNumber()
    {
        return _protocolNumber;
    }

    public void setProtocolNumber(String protocolNumber)
    {
        verifyMutability();
        _protocolNumber = protocolNumber;
    }

    public String getPtid()
    {
        return _ptid;
    }

    public void setPtid(String ptid)
    {
        verifyMutability();
        _ptid = ptid;
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        verifyMutability();
        _rowId = rowId;
    }

    public Date getSalReceiptDate()
    {
        return _salReceiptDate;
    }

    public void setSalReceiptDate(Date salReceiptDate)
    {
        verifyMutability();
        _salReceiptDate = salReceiptDate;
    }

    public String getSubAdditiveDerivative()
    {
        return _subAdditiveDerivative;
    }

    public void setSubAdditiveDerivative(String subAdditiveDerivative)
    {
        verifyMutability();
        _subAdditiveDerivative = subAdditiveDerivative;
    }

    public String getVisitDescription()
    {
        return _visitDescription;
    }

    public void setVisitDescription(String visitDescription)
    {
        verifyMutability();
        _visitDescription = visitDescription;
    }

    public Double getVisitValue()
    {
        return _visitValue;
    }

    public void setVisitValue(Double visitValue)
    {
        verifyMutability();
        _visitValue = visitValue;
    }

    public Float getVolume()
    {
        return _volume;
    }

    public void setVolume(Float volume)
    {
        verifyMutability();
        _volume = volume;
    }

    public String getVolumeUnits()
    {
        return _volumeUnits;
    }

    public void setVolumeUnits(String volumeUnits)
    {
        verifyMutability();
        _volumeUnits = volumeUnits;
    }

    public Integer getOriginatingLocationId()
    {
        return _originatingLocationId;
    }

    public void setOriginatingLocationId(Integer originatingLocationId)
    {
        _originatingLocationId = originatingLocationId;
    }

    public Integer getCurrentLocation()
    {
        return _currentLocation;
    }

    public void setCurrentLocation(Integer currentLocation)
    {
        _currentLocation = currentLocation;
    }

    public String getSampleDescription()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Global ID ").append(_globalUniqueId);
        builder.append(", ").append(StudyService.get().getSubjectNounSingular(getContainer())).append(" ").append(_ptid);
        builder.append(", ").append(_visitDescription).append(" ").append(_visitValue);
        return builder.toString();
    }

    public String getSpecimenHash()
    {
        return _specimenHash;
    }

    public void setSpecimenHash(String specimenHash)
    {
        _specimenHash = specimenHash;
    }

    public Integer getDerivativeTypeId2()
    {
        return _derivativeTypeId2;
    }

    public void setDerivativeTypeId2(Integer derivativeTypeId2)
    {
        _derivativeTypeId2 = derivativeTypeId2;
    }

    public Date getFrozenTime()
    {
        return _frozenTime;
    }

    public void setFrozenTime(Date frozenTime)
    {
        _frozenTime = frozenTime;
    }

    public Date getProcessingTime()
    {
        return _processingTime;
    }

    public void setProcessingTime(Date processingTime)
    {
        _processingTime = processingTime;
    }

    public Float getPrimaryVolume()
    {
        return _primaryVolume;
    }

    public void setPrimaryVolume(Float primaryVolume)
    {
        _primaryVolume = primaryVolume;
    }

    public String getPrimaryVolumeUnits()
    {
        return _primaryVolumeUnits;
    }

    public void setPrimaryVolumeUnits(String primaryVolumeUnits)
    {
        _primaryVolumeUnits = primaryVolumeUnits;
    }

    public Integer getProcessingLocation()
    {
        return _processingLocation;
    }

    public void setProcessingLocation(Integer processingLocation)
    {
        _processingLocation = processingLocation;
    }

    public boolean isAtRepository()
    {
        return _atRepository;
    }

    public void setAtRepository(boolean atRepository)
    {
        _atRepository = atRepository;
    }

    public boolean isAvailable()
    {
        return _available;
    }

    public void setAvailable(boolean available)
    {
        _available = available;
    }

    public boolean isLockedInRequest()
    {
        return _lockedInRequest;
    }

    public void setLockedInRequest(boolean lockedInRequest)
    {
        _lockedInRequest = lockedInRequest;
    }

    public Boolean isRequestable()
    {
        return _requestable;
    }

    public void setRequestable(Boolean requestable)
    {
        _requestable = requestable;
    }

    public String getFirstProcessedByInitials()
    {
        return _firstProcessedByInitials;
    }

    public void setFirstProcessedByInitials(String firstProcessedByInitials)
    {
        _firstProcessedByInitials = firstProcessedByInitials;
    }

    public String getAvailabilityReason()
    {
        return _availabilityReason;
    }

    public void setAvailabilityReason(String availabilityReason)
    {
        _availabilityReason = availabilityReason;
    }

    public String getLatestDeviationCode1()
    {
        return _latestDeviationCode1;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLatestDeviationCode1(String latestDeviationCode1)
    {
        _latestDeviationCode1 = latestDeviationCode1;
    }

    public String getLatestDeviationCode2()
    {
        return _latestDeviationCode2;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLatestDeviationCode2(String latestDeviationCode2)
    {
        _latestDeviationCode2 = latestDeviationCode2;
    }

    public String getLatestDeviationCode3()
    {
        return _latestDeviationCode3;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLatestDeviationCode3(String latestDeviationCode3)
    {
        _latestDeviationCode3 = latestDeviationCode3;
    }

    public String getLatestComments()
    {
        return _latestComments;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLatestComments(String comments)
    {
        _latestComments = comments;
    }

    public String getLatestQualityComments()
    {
        return _latestQualityComments;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLatestQualityComments(String latestQualityComments)
    {
        _latestQualityComments = latestQualityComments;
    }

    public Float getLatestYield()
    {
        return _latestYield;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLatestYield(Float latestYield)
    {
        _latestYield = latestYield;
    }

    public Float getLatestConcentration()
    {
        return _latestConcentration;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLatestConcentration(Float latestConcentration)
    {
        _latestConcentration = latestConcentration;
    }

    public Float getLatestRatio()
    {
        return _latestRatio;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLatestRatio(Float latestRatio)
    {
        _latestRatio = latestRatio;
    }

    public Float getLatestIntegrity()
    {
        return _latestIntegrity;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLatestIntegrity(Float latestIntegrity)
    {
        _latestIntegrity = latestIntegrity;
    }
}
