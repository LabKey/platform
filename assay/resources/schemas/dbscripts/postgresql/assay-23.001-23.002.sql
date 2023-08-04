ALTER TABLE Assay.Well ADD COLUMN SampleId INTEGER NULL;
ALTER TABLE Assay.Well ADD CONSTRAINT FK_SampleId_ExpMaterial FOREIGN KEY (SampleId) REFERENCES exp.material (RowId);

CREATE TABLE assay.PlateProperty
(
    RowId SERIAL,
    PlateId INT NOT NULL,
    PropertyId INT NOT NULL,
    PropertyURI VARCHAR(300) NOT NULL,

    CONSTRAINT PK_PlateProperty PRIMARY KEY (RowId),
    CONSTRAINT UQ_PlateProperty_PlateId_PropertyId UNIQUE (PlateId, PropertyId),
    CONSTRAINT FK_PlateProperty_PlateId FOREIGN KEY (PlateId) REFERENCES assay.Plate(RowId),
    CONSTRAINT FK_PlateProperty_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor(PropertyId)
);
