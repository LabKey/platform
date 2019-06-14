/*
 * Copyright (c) 2019 LabKey Corporation
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
