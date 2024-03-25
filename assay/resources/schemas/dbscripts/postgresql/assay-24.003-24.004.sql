ALTER TABLE assay.PlateSet ADD COLUMN Type VARCHAR(64);
ALTER TABLE assay.PlateSet ADD COLUMN RootPlateSetId INT;
ALTER TABLE assay.PlateSet ADD COLUMN PrimaryPlateSetId INT;
ALTER TABLE assay.PlateSet ADD CONSTRAINT FK_PlateSet_RootPlateSetId FOREIGN KEY (RootPlateSetId) REFERENCES assay.PlateSet (RowId);
ALTER TABLE assay.PlateSet ADD CONSTRAINT FK_PlateSet_PrimaryPlateSetId FOREIGN KEY (PrimaryPlateSetId) REFERENCES assay.PlateSet (RowId);

-- Update all pre-existing plate sets to type "assay"
UPDATE assay.PlateSet SET type = 'assay';

ALTER TABLE assay.PlateSet ALTER COLUMN Type SET NOT NULL;

CREATE TABLE assay.PlateSetEdge
(
    FromPlateSetId INT NOT NULL,
    ToPlateSetId INT NOT NULL,
    RootPlateSetId INT NOT NULL,

    CONSTRAINT FK_PlateSet_FromPlate FOREIGN KEY (FromPlateSetId) REFERENCES assay.PlateSet (RowId),
    CONSTRAINT FK_PlateSet_ToPlate FOREIGN KEY (ToPlateSetId) REFERENCES assay.PlateSet (RowId),
    CONSTRAINT FK_PlateSet_RootPlate FOREIGN KEY (RootPlateSetId) REFERENCES assay.PlateSet (RowId),
    CONSTRAINT UQ_PlateSetEdge_FromPlate_ToPlate UNIQUE (FromPlateSetId, ToPlateSetId)
);

CREATE INDEX IX_PlateSetEdge_FromPlateSetId ON assay.PlateSetEdge (FromPlateSetId);
CREATE INDEX IX_PlateSetEdge_ToPlateSetId ON assay.PlateSetEdge (ToPlateSetId);
CREATE INDEX IX_PlateSetEdge_RootPlateSetId ON assay.PlateSetEdge (RootPlateSetId);

