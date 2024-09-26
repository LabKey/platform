ALTER TABLE assay.platesetproperty
    ADD FieldKey NVARCHAR(256);
GO

ALTER TABLE assay.platesetproperty
    ADD CONSTRAINT either_identifier
        CHECK (PropertyURI IS NOT NULL OR FieldKey IS NOT NULL);

ALTER TABLE assay.platesetproperty ALTER COLUMN PropertyURI NVARCHAR(300) NULL;
ALTER TABLE assay.platesetproperty ALTER COLUMN PropertyId INT NULL;

ALTER TABLE assay.platesetproperty DROP CONSTRAINT UQ_PlateSetProperty_PlateSetId_PropertyId;
CREATE UNIQUE INDEX UQ_PlateSetProperty_PlateSetId_PropertyId ON assay.platesetproperty (PlateSetId, PropertyId) WHERE PropertyId IS NOT NULL;

SELECT core.executeJavaUpgradeCode('updateBuiltInColumns');
