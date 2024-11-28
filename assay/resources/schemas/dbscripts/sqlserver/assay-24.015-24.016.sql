CREATE TABLE assay.FilterCriteria
(
    RowId INT IDENTITY(1,1),
    PropertyId INT NOT NULL,
    ReferencePropertyId INT NOT NULL,
    DomainId INT NOT NULL,
    Operation NVARCHAR(50) NOT NULL,
    Value NVARCHAR(4000) NULL,

    CONSTRAINT PK_FilterCriteria PRIMARY KEY (RowId),
    CONSTRAINT FK_FilterCriteria_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId) ON DELETE CASCADE

    -- SQL Server does not allow for multiple foreign keys to the same table to utilize ON DELETE CASCADE as it may
    -- cause cycles or multiple cascade paths. The solution is to only ON DELETE CASCADE for one foreign key and
    -- clean up upon delete of the property for other changes. See AssayResultDomainKind.deletePropertyDescriptor().
    CONSTRAINT FK_FilterCriteria_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId) ON DELETE CASCADE,
    CONSTRAINT FK_FilterCriteria_PropertyDescriptor_Reference FOREIGN KEY (ReferencePropertyId) REFERENCES exp.PropertyDescriptor (PropertyId) ON DELETE NO ACTION,
);
