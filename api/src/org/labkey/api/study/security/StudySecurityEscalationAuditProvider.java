package org.labkey.api.study.security;

import org.labkey.api.audit.query.AbstractAuditDomainKind;

/**
 * @see SecurityEscalationAuditProvider
 */
public class StudySecurityEscalationAuditProvider extends SecurityEscalationAuditProvider {
    public static String EVENT_TYPE = StudySecurityEscalationEvent.class.getName();
    public static String AUDIT_LOG_TITLE = "Study Security Escalations";

    @Override
    public String getDescription() {
        return "This audits all uses of the Study Security Escalation";
    }

    @Override
    public Class<? extends SecurityEscalationEvent> getEventClass() {
        return StudySecurityEscalationEvent.class;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @Override
    public String getAuditLogTitle() {
        return AUDIT_LOG_TITLE;
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind() {
        return new StudySecurityEscalationDomain();
    }

    public static class StudySecurityEscalationEvent extends SecurityEscalationEvent {
        @Override
        public String getEventType() {
            return EVENT_TYPE;
        }
    }

    public static class StudySecurityEscalationDomain extends SecurityEscalationAuditDomainKind {
        public StudySecurityEscalationDomain() {
            super(EVENT_TYPE);
        }

        @Override
        public String getDomainName() {
            return StudySecurityEscalationDomain.class.getName();
        }
    }
}
