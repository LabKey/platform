/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
    private long _runGroup;
    private String _message;
    private Long _qcState;

    /** Important for reflection-based instantiation */
    @SuppressWarnings("unused")
    public ExperimentAuditEvent()
    {
        super();
    }

    public ExperimentAuditEvent(Container container, String comment)
    {
        super(EVENT_TYPE, container, comment);
        setTransactionEvent(TransactionAuditProvider.getCurrentTransactionAuditEvent(), EVENT_TYPE);
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

    public long getRunGroup()
    {
        return _runGroup;
    }

    public void setRunGroup(long runGroup)
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

    public Long getQcState()
    {
        return _qcState;
    }

    public void setQcState(Long qcState)
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


