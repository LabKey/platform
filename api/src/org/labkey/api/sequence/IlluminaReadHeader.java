package org.labkey.api.sequence;

/**
* User: jeckels
* Date: 5/29/14
*/
public class IlluminaReadHeader
{
    private String _instrument;
    private int _runId;
    private String _flowCellId;
    private int _flowCellLane;
    private int _tileNumber;
    private int _xCoord;
    private int _yCoord;
    private int _pairNumber;
    private boolean _failedFilter;
    private int _controlBits;
    private int _sampleNum;

    public IlluminaReadHeader(String header) throws IllegalArgumentException
    {
        try
        {
            String[] h = header.split(":| ");

            if(h.length < 10)
                throw new IllegalArgumentException("Improperly formatted header: " + header);

            _instrument = h[0];
            _runId = Integer.parseInt(h[1]);
            _flowCellId = h[2];
            _flowCellLane = Integer.parseInt(h[3]);
            _tileNumber = Integer.parseInt(h[4]);
            _xCoord = Integer.parseInt(h[5]);
            _yCoord = Integer.parseInt(h[6]);
            _pairNumber = Integer.parseInt(h[7]);
            setFailedFilter(h[8]);
            _controlBits = Integer.parseInt(h[9]);
            _sampleNum = Integer.parseInt(h[10]);
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public String getInstrument()
    {
        return _instrument;
    }

    public void setInstrument(String instrument)
    {
        _instrument = instrument;
    }

    public int getRunId()
    {
        return _runId;
    }

    public void setRunId(int runId)
    {
        _runId = runId;
    }

    public String getFlowCellId()
    {
        return _flowCellId;
    }

    public void setFlowCellId(String flowCellId)
    {
        _flowCellId = flowCellId;
    }

    public int getFlowCellLane()
    {
        return _flowCellLane;
    }

    public void setFlowCellLane(int flowCellLane)
    {
        _flowCellLane = flowCellLane;
    }

    public int getTileNumber()
    {
        return _tileNumber;
    }

    public void setTileNumber(int tileNumber)
    {
        _tileNumber = tileNumber;
    }

    public int getxCoord()
    {
        return _xCoord;
    }

    public void setxCoord(int xCoord)
    {
        _xCoord = xCoord;
    }

    public int getyCoord()
    {
        return _yCoord;
    }

    public void setyCoord(int yCoord)
    {
        _yCoord = yCoord;
    }

    public int getPairNumber()
    {
        return _pairNumber;
    }

    public void setPairNumber(int pairNumber)
    {
        _pairNumber = pairNumber;
    }

    public boolean isFailedFilter()
    {
        return _failedFilter;
    }

    public void setFailedFilter(boolean failedFilter)
    {
        _failedFilter = failedFilter;
    }

    public void setFailedFilter(String failedFilter)
    {
        _failedFilter = "Y".equals(failedFilter) ? true : false;
    }

    public int getControlBits()
    {
        return _controlBits;
    }

    public void setControlBits(int controlBits)
    {
        _controlBits = controlBits;
    }

    public int getSampleNum()
    {
        return _sampleNum;
    }

    public void setSampleNum(int sampleNum)
    {
        this._sampleNum = sampleNum;
    }
}
