package org.labkey.pipeline.analysis;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Audit logger for the creation, deletion, or archival of pipeline protocols.
 * User: tgaluhn
 * Date: 11/9/2016
 */
public class ProtocolManagementAuditProvider extends AbstractAuditTypeProvider
{
    public static final String EVENT = "pipelineProtocolEvent";
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ImpersonatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ProjectId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts("Comment"));
    }


    @Override
    public String getEventName()
    {
        return EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Pipeline protocol events";
    }

    @Override
    public String getDescription()
    {
        return "Information about pipeline protocol creation, deletion, or archival.";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)AuditTypeEvent.class;
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new ProtocolManagementDomainKind();
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public static class ProtocolManagementDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "PipelineProtocolDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        public ProtocolManagementDomainKind()
        {
            super(EVENT);
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return Collections.emptySet();
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }
}
