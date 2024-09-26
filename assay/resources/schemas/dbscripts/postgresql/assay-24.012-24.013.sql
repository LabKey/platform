ALTER TABLE assay.platesetproperty
    ADD COLUMN FieldKey VARCHAR(256);

ALTER TABLE assay.platesetproperty
    ADD CONSTRAINT either_identifier
        CHECK (PropertyURI IS NOT NULL OR FieldKey IS NOT NULL);

ALTER TABLE assay.platesetproperty ALTER COLUMN PropertyURI DROP NOT NULL;
ALTER TABLE assay.platesetproperty ALTER COLUMN PropertyId DROP NOT NULL;

SELECT core.executeJavaUpgradeCode('updateBuiltInColumns');
