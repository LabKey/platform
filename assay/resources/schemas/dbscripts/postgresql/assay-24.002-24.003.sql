-- Provisioned schema used by PlateMetadataDomainKind
CREATE SCHEMA assaywell;

-- upgrade script to initialize plate and plateSet IDs
SELECT core.executeJavaUpgradeCode('deletePlateVocabDomains');
