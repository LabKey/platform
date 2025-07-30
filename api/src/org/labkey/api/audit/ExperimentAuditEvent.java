package org.labkey.api.audit;

import org.labkey.api.data.Container;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExperimentAuditEvent extends AuditTypeEvent
{
    public static final String EVENT_TYPE = "ExperimentAuditEvent";

    private String _protocolLsid;
    private String _runLsid;
    private String _protocolRun;
    private int _runGroup;
    private String _message;
    private Integer _qcState;

    /** Important for reflection-based instantiation */
    @SuppressWarnings("unused")
    public ExperimentAuditEvent()
    {
        super();
        setTransactionId(TransactionAuditProvider.getCurrentTransactionAuditId());
    }

    public ExperimentAuditEvent(Container container, String comment)
    {
        super(EVENT_TYPE, container, comment);
        setTransactionId(TransactionAuditProvider.getCurrentTransactionAuditId());
    }

    public String getProtocolLsid()
    {
        return _protocolLsid;
    }

    public void setProtocolLsid(String protocolLsid)
    {
        _protocolLsid = protocolLsid;
    }

    public String getRunLsid()
    {
        return _runLsid;
    }

    public void setRunLsid(String runLsid)
    {
        _runLsid = runLsid;
    }

    public String getProtocolRun()
    {
        return _protocolRun;
    }

    public void setProtocolRun(String protocolRun)
    {
        _protocolRun = protocolRun;
    }

    public int getRunGroup()
    {
        return _runGroup;
    }

    public void setRunGroup(int runGroup)
    {
        _runGroup = runGroup;
    }

    public String getMessage()
    {
        return _message;
    }

    public void setMessage(String message)
    {
        _message = message;
    }

    public Integer getQcState()
    {
        return _qcState;
    }

    public void setQcState(Integer qcState)
    {
        _qcState = qcState;
    }

    @Override
    public Map<String, Object> getAuditLogMessageElements()
    {
        Map<String, Object> elements = new LinkedHashMap<>();
        elements.put("protocolLsid", getProtocolLsid());
        elements.put("protocolRun", getProtocolRun());
        elements.put("runGroup", getRunGroup());
        elements.put("runLsid", getRunLsid());
        elements.put("message", getMessage());
        elements.put("qcState", getQcState());
        elements.put("userComment", getUserComment());
        elements.put("transactionId", getTransactionId());
        elements.putAll(super.getAuditLogMessageElements());
        return elements;
    }
}


