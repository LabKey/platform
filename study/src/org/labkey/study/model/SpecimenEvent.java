/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
 * Date: Mar 15, 2006
 * Time: 4:35:28 PM
 */
public class SpecimenEvent extends AbstractStudyCachable<SpecimenEvent>
{
    private int _rowId; // INT IDENTITY(1,1),
    private Container _container; // ENTITYID NOT NULL,
    private int _externalId; // INT NOT NULL,
    private int _vialId; // INT NOT NULL,
    private Integer _labId; // INT,
    private String _uniqueSpecimenId; // NVARCHAR(20),
    private Integer _parentSpecimenId; // INT,
    private Integer _stored; // INT,
    private Integer _storageFlag; // INT,
    private String _storageDate; // DATETIME,
    private Integer _shipFlag; // INT,
    private Integer _shipBatchNumber; // INT,
    private Date _shipDate; // DATETIME,
    private Integer _importedBatchNumber; // INT,
    private Date _labReceiptDate; // DATETIME,
    private String _comments; // NVARCHAR(30),
    private String _specimenCondition; // NVARCHAR(3),
    private Integer _sampleNumber; // INT,
    private String _xSampleOrigin; // NVARCHAR(20),
    private String _externalLocation; // NVARCHAR(20),
    private Date _updateTimestamp; // DATETIME,
    private String _recordSource; // NVARCHAR(10),
    private String _otherSpecimenId; // NVARCHAR(20),
    private Float _expectedTimeValue; // FLOAT,
    private String _expectedTimeUnit; // NVARCHAR(15),
    private Integer _groupProtocol; // INT,
    private String _specimenNumber; // NVARCHAR(50),
    private String _ptid;
    private Date _drawTimestamp;
    private String _salReceiptDate;
    private String _classId;
    private Double _visitValue;
    private String _protocolNumber;
    private String _visitDescription;
    private Float _volume;
    private String _volumeUnits;
    private String _subAdditiveDerivative;
    private Integer _primaryTypeId;
    private Integer _derivativeTypeId;
    private Integer _additiveTypeId;
    private Integer _derivativeTypeId2;
    private Integer _originatingLocationId;
    private String _frozenTime;
    private String _processingTime;
    private String _primaryVolume;
    private String _primaryVolumeUnits;
    private String _processedByInitials;
    private Date _processingDate;
    private String _deviationCode1;
    private String _deviationCode2;
    private String _deviationCode3;
    private String _qualityComments;
    private Float _yield;
    private Float _concentration;
    private Float _ratio;
    private Float _integrity;

    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        verifyMutability();
        _comments = comments;
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

    public Integer getImportedBatchNumber()
    {
        return _importedBatchNumber;
    }

    public void setImportedBatchNumber(Integer importedBatchNumber)
    {
        verifyMutability();
        _importedBatchNumber = importedBatchNumber;
    }

    public Integer getLabId()
    {
        return _labId;
    }

    public void setLabId(Integer labId)
    {
        verifyMutability();
        _labId = labId;
    }

    public Date getLabReceiptDate()
    {
        return _labReceiptDate;
    }

    public void setLabReceiptDate(Date labReceiptDate)
    {
        verifyMutability();
        _labReceiptDate = labReceiptDate;
    }

    public Integer getParentSpecimenId()
    {
        return _parentSpecimenId;
    }

    public void setParentSpecimenId(Integer parentSpecimenId)
    {
        verifyMutability();
        _parentSpecimenId = parentSpecimenId;
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

    public int getExternalId()
    {
        return _externalId;
    }

    public void setExternalId(int externalId)
    {
        verifyMutability();
        _externalId = externalId;
    }

    public Integer getShipBatchNumber()
    {
        return _shipBatchNumber;
    }

    public void setShipBatchNumber(Integer shipBatchNumber)
    {
        verifyMutability();
        _shipBatchNumber = shipBatchNumber;
    }

    public Date getShipDate()
    {
        return _shipDate;
    }

    public void setShipDate(Date shipDate)
    {
        verifyMutability();
        _shipDate = shipDate;
    }

    public Integer getShipFlag()
    {
        return _shipFlag;
    }

    public void setShipFlag(Integer shipFlag)
    {
        verifyMutability();
        _shipFlag = shipFlag;
    }

    public int getVialId()
    {
        return _vialId;
    }

    public void setVialId(int vialId)
    {
        verifyMutability();
        _vialId = vialId;
    }

    public String getStorageDate()
    {
        return _storageDate;
    }

    public void setStorageDate(String storageDate)
    {
        verifyMutability();
        _storageDate = storageDate;
    }

    public Integer getStorageFlag()
    {
        return _storageFlag;
    }

    public void setStorageFlag(Integer storageFlag)
    {
        verifyMutability();
        _storageFlag = storageFlag;
    }

    public Integer getStored()
    {
        return _stored;
    }

    public void setStored(Integer stored)
    {
        verifyMutability();
        _stored = stored;
    }

    public String getUniqueSpecimenId()
    {
        return _uniqueSpecimenId;
    }

    public void setUniqueSpecimenId(String uniqueSpecimenId)
    {
        verifyMutability();
        _uniqueSpecimenId = uniqueSpecimenId;
    }


    public String getExpectedTimeUnit()
    {
        return _expectedTimeUnit;
    }

    public void setExpectedTimeUnit(String expectedTimeUnit)
    {
        verifyMutability();
        _expectedTimeUnit = expectedTimeUnit;
    }

    public Float getExpectedTimeValue()
    {
        return _expectedTimeValue;
    }

    public void setExpectedTimeValue(Float expectedTimeValue)
    {
        verifyMutability();
        _expectedTimeValue = expectedTimeValue;
    }

    public String getExternalLocation()
    {
        return _externalLocation;
    }

    public void setExternalLocation(String externalLocation)
    {
        verifyMutability();
        _externalLocation = externalLocation;
    }

    public String getOtherSpecimenId()
    {
        return _otherSpecimenId;
    }

    public void setOtherSpecimenId(String otherSpecimenId)
    {
        verifyMutability();
        _otherSpecimenId = otherSpecimenId;
    }


    public String getRecordSource()
    {
        return _recordSource;
    }

    public void setRecordSource(String recordSource)
    {
        verifyMutability();
        _recordSource = recordSource;
    }

    public Integer getSampleNumber()
    {
        return _sampleNumber;
    }

    public void setSampleNumber(Integer sampleNumber)
    {
        verifyMutability();
        _sampleNumber = sampleNumber;
    }

    public String getSpecimenCondition()
    {
        return _specimenCondition;
    }

    public void setSpecimenCondition(String specimenCondition)
    {
        verifyMutability();
        _specimenCondition = specimenCondition;
    }

    public Date getUpdateTimestamp()
    {
        return _updateTimestamp;
    }

    public void setUpdateTimestamp(Date updateTimestamp)
    {
        verifyMutability();
        _updateTimestamp = updateTimestamp;
    }

    public String getXSampleOrigin()
    {
        return _xSampleOrigin;
    }

    public void setXSampleOrigin(String xSampleOrigin)
    {
        verifyMutability();
        _xSampleOrigin = xSampleOrigin;
    }

    public Integer getGroupProtocol()
    {
        return _groupProtocol;
    }

    public void setGroupProtocol(Integer groupProtocol)
    {
        verifyMutability();
        _groupProtocol = groupProtocol;
    }

    public String getSpecimenNumber()
    {
        return _specimenNumber;
    }

    public void setSpecimenNumber(String specimenNumber)
    {
        verifyMutability();
        _specimenNumber = specimenNumber;
    }

    public String getPtid()
    {
        return _ptid;
    }

    public void setPtid(String ptid)
    {
        _ptid = ptid;
    }

    public Date getDrawTimestamp()
    {
        return _drawTimestamp;
    }

    public void setDrawTimestamp(Date drawTimestamp)
    {
        _drawTimestamp = drawTimestamp;
    }

    public String getSalReceiptDate()
    {
        return _salReceiptDate;
    }

    public void setSalReceiptDate(String salReceiptDate)
    {
        _salReceiptDate = salReceiptDate;
    }

    public String getClassId()
    {
        return _classId;
    }

    public void setClassId(String classId)
    {
        _classId = classId;
    }

    public Double getVisitValue()
    {
        return _visitValue;
    }

    public void setVisitValue(Double visitValue)
    {
        _visitValue = visitValue;
    }

    public String getProtocolNumber()
    {
        return _protocolNumber;
    }

    public void setProtocolNumber(String protocolNumber)
    {
        _protocolNumber = protocolNumber;
    }

    public String getVisitDescription()
    {
        return _visitDescription;
    }

    public void setVisitDescription(String visitDescription)
    {
        _visitDescription = visitDescription;
    }

    public Float getVolume()
    {
        return _volume;
    }

    public void setVolume(Float volume)
    {
        _volume = volume;
    }

    public String getVolumeUnits()
    {
        return _volumeUnits;
    }

    public void setVolumeUnits(String volumeUnits)
    {
        _volumeUnits = volumeUnits;
    }

    public String getSubAdditiveDerivative()
    {
        return _subAdditiveDerivative;
    }

    public void setSubAdditiveDerivative(String subAdditiveDerivative)
    {
        _subAdditiveDerivative = subAdditiveDerivative;
    }

    public Integer getPrimaryTypeId()
    {
        return _primaryTypeId;
    }

    public void setPrimaryTypeId(Integer primaryTypeId)
    {
        _primaryTypeId = primaryTypeId;
    }

    public Integer getDerivativeTypeId()
    {
        return _derivativeTypeId;
    }

    public void setDerivativeTypeId(Integer derivativeTypeId)
    {
        _derivativeTypeId = derivativeTypeId;
    }

    public Integer getAdditiveTypeId()
    {
        return _additiveTypeId;
    }

    public void setAdditiveTypeId(Integer additiveTypeId)
    {
        _additiveTypeId = additiveTypeId;
    }

    public Integer getDerivativeTypeId2()
    {
        return _derivativeTypeId2;
    }

    public void setDerivativeTypeId2(Integer derivativeTypeId2)
    {
        _derivativeTypeId2 = derivativeTypeId2;
    }

    public Integer getOriginatingLocationId()
    {
        return _originatingLocationId;
    }

    public void setOriginatingLocationId(Integer originatingLocationId)
    {
        _originatingLocationId = originatingLocationId;
    }

    public String getFrozenTime()
    {
        return _frozenTime;
    }

    public void setFrozenTime(String frozenTime)
    {
        _frozenTime = frozenTime;
    }

    public String getProcessingTime()
    {
        return _processingTime;
    }

    public void setProcessingTime(String processingTime)
    {
        _processingTime = processingTime;
    }

    public String getPrimaryVolume()
    {
        return _primaryVolume;
    }

    public void setPrimaryVolume(String primaryVolume)
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

    public String getProcessedByInitials()
    {
        return _processedByInitials;
    }

    public void setProcessedByInitials(String processedByInitials)
    {
        _processedByInitials = processedByInitials;
    }

    public Date getProcessingDate()
    {
        return _processingDate;
    }

    public void setProcessingDate(Date processingDate)
    {
        _processingDate = processingDate;
    }

    public String getDeviationCode1()
    {
        return _deviationCode1;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setDeviationCode1(String deviationCode1)
    {
        _deviationCode1 = deviationCode1;
    }

    public String getDeviationCode2()
    {
        return _deviationCode2;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setDeviationCode2(String deviationCode2)
    {
        _deviationCode2 = deviationCode2;
    }

    public String getDeviationCode3()
    {
        return _deviationCode3;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setDeviationCode3(String deviationCode3)
    {
        _deviationCode3 = deviationCode3;
    }

    public String getQualityComments()
    {
        return _qualityComments;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setQualityComments(String qualityComments)
    {
        _qualityComments = qualityComments;
    }

    public Float getYield()
    {
        return _yield;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setYield(Float yield)
    {
        _yield = yield;
    }

    public Float getConcentration()
    {
        return _concentration;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setConcentration(Float concentration)
    {
        _concentration = concentration;
    }

    public Float getRatio()
    {
        return _ratio;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setRatio(Float ratio)
    {
        _ratio = ratio;
    }

    public Float getIntegrity()
    {
        return _integrity;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setIntegrity(Float integrity)
    {
        _integrity = integrity;
    }
}
