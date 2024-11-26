CREATE TABLE assay.HitCriteria
(
    RowId SERIAL,
    PropertyId INT NOT NULL,
    ReferencePropertyId INT NOT NULL,
    DomainId INT NOT NULL,
    Operation VARCHAR(50) NOT NULL,
    Value VARCHAR(4000) NULL,

    CONSTRAINT PK_HitCriteria PRIMARY KEY (RowId),
    CONSTRAINT FK_HitCriteria_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId) ON DELETE CASCADE,
    CONSTRAINT FK_HitCriteria_PropertyDescriptor_Reference FOREIGN KEY (ReferencePropertyId) REFERENCES exp.PropertyDescriptor (PropertyId) ON DELETE CASCADE,
    CONSTRAINT FK_HitCriteria_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId) ON DELETE CASCADE
);
