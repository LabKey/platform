package org.labkey.experiment;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/21/13
 */
public class ExperimentAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EXPERIMENT_AUDIT_EVENT = "ExperimentAuditEvent";

    @Override
    protected DomainKind getDomainKind()
    {
        return new ExperimentAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EXPERIMENT_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Assay/Experiment events";
    }

    @Override
    public String getDescription()
    {
        return "Describes information about assay run events.";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        ExperimentAuditEvent bean = new ExperimentAuditEvent();
        copyStandardFields(bean, event);

        bean.setProtocolLsid(event.getKey1());
        bean.setRunLsid(event.getKey2());
        bean.setProtocolRun(event.getKey3());

        if (event.getIntKey1() != null)
            bean.setRunGroup(event.getIntKey1());

        return (K)bean;
    }

    public static class ExperimentAuditEvent extends AuditTypeEvent
    {
        private String _protocolLsid;
        private String _runLsid;
        private String _protocolRun;
        private int _runGroup;

        public ExperimentAuditEvent()
        {
            super();
        }

        public ExperimentAuditEvent(String container, String comment)
        {
            super(EXPERIMENT_AUDIT_EVENT, container, comment);
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
    }

    public static class ExperimentAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ExperimentAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec("ProtocolLsid", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("RunLsid", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("ProtocolRun", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("RunGroup", JdbcType.INTEGER));
        }

        @Override
        protected Set<PropertyStorageSpec> getColumns()
        {
            return _fields;
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }
}
