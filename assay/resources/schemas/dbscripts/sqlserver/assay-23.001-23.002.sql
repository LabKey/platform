ALTER TABLE Assay.Well ADD SampleId INTEGER NULL;
ALTER TABLE Assay.Well ADD CONSTRAINT FK_SampleId_ExpMaterial FOREIGN KEY (SampleId) REFERENCES exp.material (RowId);

CREATE TABLE assay.PlateProperty
(
    RowId INT IDENTITY(1,1),
    PlateId INT NOT NULL,
    PropertyId INT NOT NULL,
    PropertyURI NVARCHAR(300) NOT NULL,

    CONSTRAINT PK_PlateProperty PRIMARY KEY (RowId),
    CONSTRAINT UQ_PlateProperty_PlateId_PropertyId UNIQUE (PlateId, PropertyId),
    CONSTRAINT FK_PlateProperty_PlateId FOREIGN KEY (PlateId) REFERENCES assay.Plate(RowId),
    CONSTRAINT FK_PlateProperty_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor(PropertyId)
);
