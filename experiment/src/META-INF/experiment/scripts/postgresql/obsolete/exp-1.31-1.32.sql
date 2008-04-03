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