CREATE TABLE assay.FilterCriteria
(
    RowId SERIAL,
    PropertyId INT NOT NULL,
    ReferencePropertyId INT NOT NULL,
    DomainId INT NOT NULL,
    Operation VARCHAR(50) NOT NULL,
    Value VARCHAR(4000) NULL,

    CONSTRAINT PK_FilterCriteria PRIMARY KEY (RowId),
    CONSTRAINT FK_FilterCriteria_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId) ON DELETE CASCADE,
    CONSTRAINT FK_FilterCriteria_PropertyDescriptor_Reference FOREIGN KEY (ReferencePropertyId) REFERENCES exp.PropertyDescriptor (PropertyId) ON DELETE CASCADE,
    CONSTRAINT FK_FilterCriteria_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId) ON DELETE CASCADE
);

SELECT core.executeJavaUpgradeCode('initializeHitSelectionCriteria');
