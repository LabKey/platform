ALTER TABLE assay.platesetproperty
    ADD FieldKey NVARCHAR(256);
GO

ALTER TABLE assay.platesetproperty
    ADD CONSTRAINT either_identifier
        CHECK (PropertyURI IS NOT NULL OR FieldKey IS NOT NULL);

ALTER TABLE assay.platesetproperty ALTER COLUMN PropertyURI NVARCHAR(300) NULL;
ALTER TABLE assay.platesetproperty ALTER COLUMN PropertyId INT NULL;
