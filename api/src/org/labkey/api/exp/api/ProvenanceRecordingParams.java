package org.labkey.api.exp.api;

import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.util.GUID;

import java.io.Serializable;

public class ProvenanceRecordingParams extends RecordedAction implements Serializable
{
    private GUID _recordingId;
    private String _inputObjectUriProperty = ProvenanceService.PROVENANCE_INPUT_PROPERTY;
    private String _outputObjectUriProperty = "lsid";

    public GUID getRecordingId()
    {
        return _recordingId;
    }

    public void setRecordingId(GUID recordingId)
    {
        _recordingId = recordingId;
    }

    public String getInputObjectUriProperty()
    {
        return _inputObjectUriProperty;
    }

    public void setInputObjectUriProperty(String inputObjectUriProperty)
    {
        _inputObjectUriProperty = inputObjectUriProperty;
    }

    public String getOutputObjectUriProperty()
    {
        return _outputObjectUriProperty;
    }

    public void setOutputObjectUriProperty(String outputObjectUriProperty)
    {
        _outputObjectUriProperty = outputObjectUriProperty;
    }
}
