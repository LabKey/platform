/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
ALTER TABLE exp.PropertyDescriptor DROP COLUMN DomainURI
;
-- fix orphans from bad OntologyManager unit test
DELETE FROM exp.PropertyDescriptor
	WHERE Container = (SELECT C.EntityId FROM core.Containers C WHERE C.Name IS NULL)
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
