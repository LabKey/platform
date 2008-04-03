package org.labkey.study.model;

import org.labkey.api.data.Container;

import java.util.Date;

/**
 * User: brittp
 * Date: Mar 15, 2006
 * Time: 4:26:07 PM
 */
public class Specimen extends AbstractStudyCachable<Specimen>
{
    private int _rowId; // INT NOT NULL, -- FK exp.Material
    private Container _container; // ENTITYID NOT NULL,
    private String _globalUniqueId; // NVARCHAR(20) NOT NULL,
    private String _ptid; // NVARCHAR(32),
    private Date _drawTimestamp; // DATETIME,
    private Date _salReceiptDate; // DATETIME,
    private String _specimenNumber; // NVARCHAR(50),
    private String _classId; // NVARCHAR(4),
    private Double _visitValue; // FLOAT,
    private String _protocolNumber; // NVARCHAR(10),
    private String _visitDescription; // NVARCHAR(3),
    private Float _volume; // FLOAT,
    private String _volumeUnits; // NVARCHAR(3),
    private String _subAdditiveDerivative; // NVARCHAR(20),
    private Integer _primaryTypeId; // INT,
    private Integer _derivativeTypeId; // INT,
    private Integer _additiveTypeId; // INT,
    private Integer _originatingLocationId;

    public Integer getAdditiveTypeId()
    {
        return _additiveTypeId;
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

    public String getSpecimenNumber()
    {
        return _specimenNumber;
    }

    public void setSpecimenNumber(String specimenNumber)
    {
        verifyMutability();
        _specimenNumber = specimenNumber;
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

    public String getSampleDescription()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Global ID ").append(_globalUniqueId);
        builder.append(", Participant ").append(_ptid);
        builder.append(", ").append(_visitDescription).append(" ").append(_visitValue);
        return builder.toString();
    }
}
