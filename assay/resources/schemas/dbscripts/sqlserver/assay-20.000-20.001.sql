CREATE TABLE assay.WellGroupPositions
(
    RowId INT IDENTITY(1,1) NOT NULL,
    WellId INT NOT NULL,
    WellGroupId INT NOT NULL,

    CONSTRAINT PK_WellGroupPositions PRIMARY KEY (RowId),
    CONSTRAINT FK_WellGroupPositions_Well FOREIGN KEY (WellId) REFERENCES assay.Well(RowId),
    CONSTRAINT FK_WellGroupPositions_WellGroup FOREIGN KEY (WellGroupId) REFERENCES assay.WellGroup(RowId),
    CONSTRAINT UQ_WellGroupPositions_WellGroup_Well UNIQUE (WellGroupId, WellId)
);

-- populate the WellGroupPositions table
-- Each well is associated with it's well group using a PropertyDescriptor that
-- has a PropertyURI with namespace prefix "WellTemplate" and objectId of "objectType#Group N"
-- where the property value is the LSID of the well group it belongs to.
INSERT INTO assay.WellGroupPositions (wellId, wellGroupId)
SELECT
    w.rowid, wg.rowid
FROM assay.Well w
         INNER JOIN exp.Object o ON o.ObjectUri = w.lsid
         INNER JOIN exp.ObjectProperty op ON o.objectId = op.objectId
         INNER JOIN exp.PropertyDescriptor pd ON op.propertyId = pd.propertyId
         INNER JOIN assay.WellGroup wg ON wg.lsid = op.stringValue
WHERE pd.propertyuri LIKE 'urn:lsid:%:WellTemplate.Folder-%:objectType#Group %';

-- The WellTemplate "Group N" properties should be unused now.
-- We can safely delete the ObjectProperty values and PropertyDescriptors
DELETE FROM exp.ObjectProperty WHERE propertyId IN (
    SELECT propertyId
    FROM exp.PropertyDescriptor
    WHERE propertyUri LIKE 'urn:lsid:%:WellTemplate.Folder-%:objectType#Group %'
);

DELETE FROM exp.propertydescriptor WHERE
        propertyUri LIKE 'urn:lsid:%:WellTemplate.Folder-%:objectType#Group %';

