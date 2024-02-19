-- Provisioned schema used by PlateMetadataDomainKind
CREATE SCHEMA assaywell;
GO

-- upgrade script to set the plate ID value in assay.Plate
EXEC core.executeJavaUpgradeCode 'deletePlateVocabDomains';
