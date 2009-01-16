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
    private int _specimenId; // INT NOT NULL,
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

    public int getSpecimenId()
    {
        return _specimenId;
    }

    public void setSpecimenId(int specimenId)
    {
        verifyMutability();
        _specimenId = specimenId;
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

}
