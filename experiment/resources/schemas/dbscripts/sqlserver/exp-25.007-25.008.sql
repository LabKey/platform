-- Update all "security escalation" domains with a proper namespace prefix
UPDATE exp.domainDescriptor SET DomainURI = REPLACE(DomainURI, '--', '-StudySecurityEscalationDomain-') WHERE StorageSchemaName = 'audit' AND (DomainURI LIKE '%StudySecurityEscalationAuditProvider' OR DomainURI LIKE '%StudySecurityEscalationEvent');
UPDATE exp.domainDescriptor SET DomainURI = REPLACE(DomainURI, '--', '-EHRSecurityEscalationDomain-') WHERE StorageSchemaName = 'audit' AND DomainURI LIKE '%EHRSecurityEscalationEvent';

-- Update LDAP sync domain with a unique namespace prefix so it doesn't overlap with the ONPRC version
UPDATE exp.domainDescriptor SET DomainURI = REPLACE(DomainURI, 'LdapSyncAuditDomain', 'PremiumLdapSyncAuditDomain') WHERE StorageSchemaName = 'audit' AND DomainURI LIKE '%PremiumLdapAuditEvent';

-- Update query audit events that advertised the same generic namespace prefix
UPDATE exp.domainDescriptor SET DomainURI = REPLACE(DomainURI, 'QueryAuditDomain', 'QueryExportAuditDomain') WHERE StorageSchemaName = 'audit' AND DomainURI LIKE '%QueryExportAuditEvent';
UPDATE exp.domainDescriptor SET DomainURI = REPLACE(DomainURI, 'QueryAuditDomain', 'LoggedQueryAuditDomain') WHERE StorageSchemaName = 'audit' AND DomainURI LIKE '%LoggedQuery';