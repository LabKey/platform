package org.labkey.api.assay.dilution;

/**
 * Created by davebradlee on 8/11/15.
 *
 */
public class WellDataRow
{
    private int _rowId;
    private int _runId;
    private String _specimenLsid;
    private Integer _runDataId;
    private Integer _dilutionDataId;
    private Integer _protocolId;
    private int _row;
    private int _column;
    private double _value;
    private String _controlWellgroup;
    private String _virusWellgroup;
    private String _replicateWellgroup;
    private Integer _replicateNumber;
    private Integer _plateNumber;
    private String _plateVirusName;
    private String _container;

    public WellDataRow()
    {

    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getRunId()
    {
        return _runId;
    }

    public void setRunId(int runId)
    {
        _runId = runId;
    }

    public String getSpecimenLsid()
    {
        return _specimenLsid;
    }

    public void setSpecimenLsid(String specimenLsid)
    {
        _specimenLsid = specimenLsid;
    }

    public Integer getRunDataId()
    {
        return _runDataId;
    }

    public void setRunDataId(Integer runDataId)
    {
        _runDataId = runDataId;
    }

    public Integer getDilutionDataId()
    {
        return _dilutionDataId;
    }

    public void setDilutionDataId(Integer dilutionDataId)
    {
        _dilutionDataId = dilutionDataId;
    }

    public Integer getProtocolId()
    {
        return _protocolId;
    }

    public void setProtocolId(Integer protocolId)
    {
        _protocolId = protocolId;
    }

    public int getRow()
    {
        return _row;
    }

    public void setRow(int row)
    {
        _row = row;
    }

    public int getColumn()
    {
        return _column;
    }

    public void setColumn(int column)
    {
        _column = column;
    }

    public double getValue()
    {
        return _value;
    }

    public void setValue(double value)
    {
        _value = value;
    }

    public String getControlWellgroup()
    {
        return _controlWellgroup;
    }

    public void setControlWellgroup(String controlWellgroup)
    {
        _controlWellgroup = controlWellgroup;
    }

    public String getVirusWellgroup()
    {
        return _virusWellgroup;
    }

    public void setVirusWellgroup(String virusWellgroup)
    {
        _virusWellgroup = virusWellgroup;
    }

    public String getReplicateWellgroup()
    {
        return _replicateWellgroup;
    }

    public void setReplicateWellgroup(String replicateWellgroup)
    {
        _replicateWellgroup = replicateWellgroup;
    }

    public Integer getReplicateNumber()
    {
        return _replicateNumber;
    }

    public void setReplicateNumber(Integer replicateNumber)
    {
        _replicateNumber = replicateNumber;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public Integer getPlateNumber()
    {
        return _plateNumber;
    }

    public void setPlateNumber(Integer plateNumber)
    {
        _plateNumber = plateNumber;
    }

    public String getPlateVirusName()
    {
        return _plateVirusName;
    }

    public void setPlateVirusName(String plateVirusName)
    {
        _plateVirusName = plateVirusName;
    }
}
