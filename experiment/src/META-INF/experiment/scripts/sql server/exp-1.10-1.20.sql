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
if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_BioSource_Material]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[BioSource] DROP CONSTRAINT FK_BioSource_Material
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_Fraction_Material]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[Fraction] DROP CONSTRAINT FK_Fraction_Material
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[BioSource]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
drop table [exp].[BioSource]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[Fraction]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
drop table [exp].[Fraction]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[setProperty]') and OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp.setProperty
GO

ALTER TABLE exp.MaterialSource
   DROP CONSTRAINT UQ_MaterialSource_Name 
GO

ALTER TABLE exp.ProtocolParameter ALTER COLUMN StringValue nvarchar(4000) NULL
go
UPDATE exp.ProtocolParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink'
go
ALTER TABLE exp.ProtocolParameter DROP COLUMN FileLinkValue
go
ALTER TABLE exp.ProtocolParameter DROP COLUMN XmlTextValue
go

ALTER TABLE exp.ProtocolApplicationParameter ALTER COLUMN StringValue nvarchar(4000) NULL
go
UPDATE exp.ProtocolApplicationParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink'
go
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN FileLinkValue
go
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN XmlTextValue
go


/*
This update makes the PropertyDescriptor more consistent with OWL terms, and also work for storing NCI_Thesaurus concepts

We're somewhat merging to concepts here.

A PropertyDescriptor with no Domain is a concept (or a Class in OWL).
A PropertyDescriptor with a Domain describes a member of a type (or an ObjectProperty in OWL)
*/

ALTER TABLE exp.ObjectProperty DROP CONSTRAINT FK_ObjectProperty_PropertyDescriptor
go

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ObjectPropertiesView]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ObjectPropertiesView
go
if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ObjectClasses]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ObjectClasses
go


ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT PK_PropertyDescriptor 
go
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT UQ_PropertyDescriptor 
go


go
EXEC sp_rename @objname = 'exp.PropertyDescriptor',       @newname = 'PropertyDescriptorOld'
go
CREATE TABLE exp.PropertyDescriptor (
	PropertyId int IDENTITY (1, 1) NOT NULL ,
	PropertyURI nvarchar (200) NOT NULL ,
	OntologyURI nvarchar (200) NULL ,
	DomainURI nvarchar (200) NULL ,
	Name nvarchar (200) NULL ,
	Description ntext NULL ,
	RangeURI nvarchar (200) NOT NULL DEFAULT ('http://www.w3.org/2001/XMLSchema#string'),
	ConceptURI nvarchar (200) NULL ,
	Label nvarchar (200) NULL ,
	SearchTerms nvarchar (1000) NULL ,
	SemanticType nvarchar (200) NULL ,
	Format nvarchar (50) NULL ,
	Container ENTITYID NOT NULL)
GO

SET IDENTITY_INSERT exp.PropertyDescriptor ON

INSERT INTO exp.PropertyDescriptor(PropertyId, PropertyURI, OntologyURI, DomainURI, Name, 
	Description, RangeURI, Container)
SELECT rowid, PropertyURI, OntologyURI, TypeURI, Name, 
	Description, DatatypeURI, 
	(SELECT  MAX(CAST (O.Container AS VARCHAR(100)))
			FROM exp.PropertyDescriptorOld PD 
				INNER JOIN exp.ObjectProperty OP ON (PD.rowid = OP.PropertyID) 
				INNER JOIN exp.Object O ON (O.ObjectId = OP.ObjectID)	
			WHERE PDU.rowid = PD.rowid
			GROUP BY PD.rowid
		)
FROM exp.PropertyDescriptorOld PDU
	WHERE PDU.rowid IN
		(SELECT PD.rowid
		FROM exp.PropertyDescriptorOld PD 
			INNER JOIN exp.ObjectProperty OP ON (PD.rowid = OP.PropertyID) 
			INNER JOIN exp.Object O ON (O.ObjectId = OP.ObjectID)	
		GROUP BY PD.rowid
		)

SET IDENTITY_INSERT exp.PropertyDescriptor OFF
go

DROP TABLE exp.PropertyDescriptorOld
go

UPDATE exp.PropertyDescriptor
SET ConceptURI = RangeURI, RangeURI = 'http://www.w3.org/2001/XMLSchema#string'
WHERE RangeURI NOT LIKE 'http://www.w3.org/2001/XMLSchema#%'
go

ALTER TABLE exp.PropertyDescriptor ADD
	CONSTRAINT PK_PropertyDescriptor PRIMARY KEY  CLUSTERED 
	(
		PropertyId
	),
	CONSTRAINT UQ_PropertyDescriptor UNIQUE  NONCLUSTERED 
	(
		PropertyURI
	)

go

ALTER TABLE exp.Object DROP CONSTRAINT UQ_Object
go
ALTER TABLE exp.Object ADD CONSTRAINT UQ_Object UNIQUE (ObjectURI)
GO

DROP INDEX exp.Object.IDX_Object_OwnerObjectId
go

CREATE CLUSTERED INDEX IDX_Object_ContainerOwnerObjectId ON exp.Object (Container, OwnerObjectId, ObjectId);
GO

ALTER TABLE exp.ObjectProperty 
	ADD CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)

go

CREATE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.*,
		PD.name, PD.PropertyURI, PD.RangeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue, P.TextValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId
go



CREATE VIEW exp.ObjectClasses AS
	SELECT DISTINCT DomainURI
	FROM exp.PropertyDescriptor
	WHERE DomainURI IS NOT NULL
go

