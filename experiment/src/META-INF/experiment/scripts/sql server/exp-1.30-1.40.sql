CREATE TABLE exp.DomainDescriptor (
	DomainId int IDENTITY (1, 1) NOT NULL ,
	Name nvarchar (200) NULL ,
	DomainURI nvarchar (200) NOT NULL ,
	Description ntext NULL ,
	Container ENTITYID NOT NULL,
	CONSTRAINT PK_DomainDescriptor PRIMARY KEY CLUSTERED (DomainId),
	CONSTRAINT UQ_DomainDescriptor UNIQUE (DomainURI))

go
CREATE TABLE exp.PropertyDomain (
	PropertyId int NOT NULL,
	DomainId int NOT NULL,
	CONSTRAINT PK_PropertyDomain PRIMARY KEY  CLUSTERED (PropertyId,DomainId),
	CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId) 
		REFERENCES exp.PropertyDescriptor (PropertyId),
	CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId) 
		REFERENCES exp.DomainDescriptor (DomainId)
	)
go

INSERT INTO exp.DomainDescriptor (DomainURI, Container)
    SELECT DomainURI, Container 
	FROM exp.PropertyDescriptor PD WHERE PD.DomainURI IS NOT NULL
	AND NOT EXISTS (SELECT * FROM exp.DomainDescriptor DD WHERE DD.DomainURI=PD.DomainURI)
	GROUP BY DomainURI, Container
go
INSERT INTO exp.PropertyDomain
    SELECT PD.PropertyId, DD.DomainId
	FROM exp.PropertyDescriptor PD INNER JOIN exp.DomainDescriptor DD
		ON (PD.DomainURI = DD.DomainURI)
go
DROP VIEW exp.ObjectClasses
go

ALTER TABLE exp.PropertyDescriptor DROP COLUMN DomainURI
go
CREATE VIEW exp.ObjectClasses AS
	SELECT DomainURI
	FROM exp.DomainDescriptor
go

-- fix orphans from bad OntologyManager unit test
DELETE FROM exp.PropertyDescriptor
WHERE Container = (SELECT C.EntityId from core.Containers C where C.Name is null)
AND PropertyURI LIKE '%Junit.OntologyManager%'
go

ALTER TABLE exp.ObjectProperty DROP CONSTRAINT FK_ObjectProperty_PropertyDescriptor
go
ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_Property
go
ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_DomainDescriptor
go
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT UQ_PropertyDescriptor
go
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT PK_PropertyDescriptor
go
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT UQ_DomainDescriptor
go
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT PK_DomainDescriptor
go

ALTER TABLE exp.PropertyDescriptor ADD Project ENTITYID NULL
go
ALTER TABLE exp.PropertyDescriptor
	ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY NONCLUSTERED (PropertyId)
go

ALTER TABLE exp.DomainDescriptor
	ADD Project ENTITYID NULL
go
ALTER TABLE exp.DomainDescriptor
	ADD CONSTRAINT PK_DomainDescriptor PRIMARY KEY NONCLUSTERED (DomainId)
go

ALTER TABLE exp.ObjectProperty ADD
	CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
go
ALTER TABLE exp.PropertyDomain ADD
	CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
go
ALTER TABLE exp.PropertyDomain ADD
	CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId)
go