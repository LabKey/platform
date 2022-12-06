package org.labkey.specimen;

import org.labkey.api.audit.AuditTypeEvent;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.labkey.specimen.SpecimenCommentAuditDomainKind.SPECIMEN_COMMENT_EVENT;

public class SpecimenCommentAuditEvent extends AuditTypeEvent
{
    private String _vialId;

    public SpecimenCommentAuditEvent()
    {
        super();
    }

    public SpecimenCommentAuditEvent(String container, String comment)
    {
        super(SPECIMEN_COMMENT_EVENT, container, comment);
    }

    public String getVialId()
    {
        return _vialId;
    }

    public void setVialId(String vialId)
    {
        _vialId = vialId;
    }

    @Override
    public Map<String, Object> getAuditLogMessageElements()
    {
        Map<String, Object> elements = new LinkedHashMap<>();
        elements.put("vialId", getVialId());
        elements.putAll(super.getAuditLogMessageElements());
        return elements;
    }
}
