package org.labkey.study.specimen;

import org.labkey.api.annotations.Migrate;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.study.assay.query.AssayAuditProvider;

import java.util.LinkedHashMap;
import java.util.Map;

@Migrate
public class SpecimenCommentAuditEvent extends AuditTypeEvent
{
    private String _vialId;

    public SpecimenCommentAuditEvent()
    {
        super();
    }

    public SpecimenCommentAuditEvent(String container, String comment)
    {
        // TODO: This looks wrong - shouldn't this be SPECIMEN_COMMENT_EVENT?!
        super(AssayAuditProvider.ASSAY_PUBLISH_AUDIT_EVENT, container, comment);
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
