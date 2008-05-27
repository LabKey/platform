ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT UQ_DomainURI
GO

ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT UQ_DomainURIContainer UNIQUE (DomainURI, Container)
GO
