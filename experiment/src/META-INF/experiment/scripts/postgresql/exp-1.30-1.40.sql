CREATE TABLE exp.DomainDescriptor (
	DomainId SERIAL NOT NULL ,
	Name varchar (200) NULL ,
	DomainURI varchar (200) NOT NULL ,
	Description text NULL ,
	Container ENTITYID NOT NULL,
	CONSTRAINT PK_DomainDescriptor PRIMARY KEY (DomainId),
	CONSTRAINT UQ_DomainDescriptor UNIQUE (DomainURI))
	;

CREATE TABLE exp.PropertyDomain (
	PropertyId int NOT NULL,
	DomainId int NOT NULL,
	CONSTRAINT PK_PropertyDomain PRIMARY KEY (PropertyId,DomainId),
	CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId) 
		REFERENCES exp.PropertyDescriptor (PropertyId),
	CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId) 
		REFERENCES exp.DomainDescriptor (DomainId)
	)
    ;
INSERT INTO exp.DomainDescriptor (DomainURI, Container) 
	SELECT DomainURI, Container 
	FROM exp.PropertyDescriptor PD WHERE PD.DomainURI IS NOT NULL
	AND NOT EXISTS (SELECT * FROM exp.DomainDescriptor DD WHERE DD.DomainURI=PD.DomainURI)
	GROUP BY DomainURI, Container
;
INSERT INTO exp.PropertyDomain 
	SELECT PD.PropertyId, DD.DomainId 
	FROM exp.PropertyDescriptor PD INNER JOIN exp.DomainDescriptor DD
		ON (PD.DomainURI = DD.DomainURI)
;
DROP VIEW exp.ObjectClasses
;

ALTER TABLE exp.PropertyDescriptor DROP COLUMN DomainURI
;
CREATE VIEW exp.ObjectClasses AS
	SELECT DomainURI
	FROM exp.DomainDescriptor
;

-- fix orphans from bad OntologyManager unit test
DELETE FROM exp.PropertyDescriptor
	WHERE Container = (SELECT C.EntityId from core.Containers C where C.Name is null)
	AND PropertyURI LIKE '%Junit.OntologyManager%'
;

ALTER TABLE exp.ObjectProperty DROP CONSTRAINT FK_ObjectProperty_PropertyDescriptor
;
ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_Property
;
ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_DomainDescriptor
;
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT UQ_PropertyDescriptor
;
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT PK_PropertyDescriptor
;
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT UQ_DomainDescriptor
;
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT PK_DomainDescriptor
;

ALTER TABLE exp.PropertyDescriptor ADD COLUMN Project ENTITYID NULL
;
ALTER TABLE exp.PropertyDescriptor
	ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (PropertyId)
;

ALTER TABLE exp.DomainDescriptor
	ADD COLUMN Project ENTITYID NULL
;
ALTER TABLE exp.DomainDescriptor
	ADD CONSTRAINT PK_DomainDescriptor PRIMARY KEY (DomainId)
;

ALTER TABLE exp.ObjectProperty ADD
	CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
;
ALTER TABLE exp.PropertyDomain ADD
	CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
;
ALTER TABLE exp.PropertyDomain ADD
	CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId)
;