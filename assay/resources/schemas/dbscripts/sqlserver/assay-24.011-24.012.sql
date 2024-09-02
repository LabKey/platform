-- Specify plate metadata columns on the plate set rather than the individual plates
CREATE TABLE assay.PlateSetProperty
(
    RowId INT IDENTITY(1,1),
    PlateSetId INT NOT NULL,
    PropertyId INT NOT NULL,
    PropertyURI NVARCHAR(300) NOT NULL,

    CONSTRAINT PK_PlateSetProperty PRIMARY KEY (RowId),
    CONSTRAINT UQ_PlateSetProperty_PlateSetId_PropertyId UNIQUE (PlateSetId, PropertyId),
    CONSTRAINT FK_PlateSetProperty_PlateSetId FOREIGN KEY (PlateSetId) REFERENCES assay.PlateSet(RowId) ON DELETE CASCADE,
    CONSTRAINT FK_PlateSetProperty_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor(PropertyId) ON DELETE CASCADE
);

INSERT INTO assay.PlateSetProperty (PlateSetId, PropertyId, PropertyURI)
SELECT
    PL.PlateSet AS PlateSetId,
    PP.PropertyId,
    PP.PropertyURI
FROM assay.PlateProperty AS PP
INNER JOIN assay.Plate AS PL ON PP.PlateId = PL.RowId
GROUP BY PL.PlateSet, PP.PropertyId, PP.PropertyURI
ORDER BY PlateSetId, PropertyId;

DROP TABLE assay.PlateProperty;
GO
