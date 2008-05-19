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
ALTER TABLE exp.BioSource DROP CONSTRAINT FK_BioSource_Material;
ALTER TABLE exp.Fraction DROP CONSTRAINT FK_Fraction_Material;
DROP TABLE exp.BioSource;
DROP TABLE exp.Fraction;
DROP FUNCTION exp.setProperty(INTEGER, LSIDType, LSIDType, CHAR(1), FLOAT, varchar(400), timestamp, TEXT);


ALTER TABLE exp.MaterialSource
   DROP CONSTRAINT UQ_MaterialSource_Name ;

ALTER TABLE exp.ProtocolParameter ALTER COLUMN StringValue TYPE VARCHAR(4000);
UPDATE exp.ProtocolParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink';
ALTER TABLE exp.ProtocolParameter DROP COLUMN FileLinkValue;
ALTER TABLE exp.ProtocolParameter DROP COLUMN XmlTextValue;

ALTER TABLE exp.ProtocolApplicationParameter ALTER COLUMN StringValue TYPE VARCHAR(4000);
UPDATE exp.ProtocolApplicationParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink';
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN FileLinkValue;
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN XmlTextValue;



--This update makes the PropertyDescriptor more consistent with OWL terms, and also work for storing NCI_Thesaurus concepts

--We're somewhat merging to concepts here.

--A PropertyDescriptor with no Domain is a concept (or a Class in OWL).
--A PropertyDescriptor with a Domain describes a member of a type (or an ObjectProperty in OWL)


ALTER TABLE exp.ObjectProperty DROP CONSTRAINT FK_ObjectProperty_PropertyDescriptor;

DROP VIEW exp.ObjectPropertiesView;
--DROP VIEW exp.ObjectClasses;


ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT PK_PropertyDescriptor ;
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT UQ_PropertyDescriptor ;

ALTER TABLE exp.PropertyDescriptor RENAME TO PropertyDescriptorOld;

CREATE TABLE exp.PropertyDescriptor (
	PropertyId SERIAL NOT NULL ,
	PropertyURI VARCHAR (200) NOT NULL ,
	OntologyURI VARCHAR (200) NULL ,
	DomainURI VARCHAR (200) NULL ,
	Name VARCHAR (200) NULL ,
	Description TEXT NULL ,
	RangeURI VARCHAR (200) NOT NULL CONSTRAINT DF_PropertyDescriptor_Range DEFAULT ('http://www.w3.org/2001/XMLSchema#string'),
	ConceptURI VARCHAR (200) NULL ,
	Label VARCHAR (200) NULL ,
	SearchTerms VARCHAR (1000) NULL ,
	SemanticType VARCHAR (200) NULL ,
	Format VARCHAR (50) NULL ,
	Container ENTITYID NOT NULL);

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
		);

DROP TABLE exp.PropertyDescriptorOld;

UPDATE exp.PropertyDescriptor
SET ConceptURI = RangeURI, RangeURI = 'http://www.w3.org/2001/XMLSchema#string'
WHERE RangeURI NOT LIKE 'http://www.w3.org/2001/XMLSchema#%';

ALTER TABLE exp.PropertyDescriptor 
	ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (PropertyId),
	ADD CONSTRAINT UQ_PropertyDescriptor UNIQUE (PropertyURI)
;

ALTER TABLE exp.Object DROP CONSTRAINT UQ_Object;
ALTER TABLE exp.Object ADD CONSTRAINT UQ_Object UNIQUE (ObjectURI);

DROP INDEX exp.IDX_Object_OwnerObjectId;

CREATE INDEX IDX_Object_ContainerOwnerObjectId ON exp.Object (Container, OwnerObjectId, ObjectId);;

ALTER TABLE exp.ObjectProperty 
	ADD CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
;

CREATE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.*,
		PD.name, PD.PropertyURI, PD.RangeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue, P.TextValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId;



CREATE VIEW exp.ObjectClasses AS
	SELECT DISTINCT DomainURI
	FROM exp.PropertyDescriptor
	WHERE DomainURI IS NOT NULL;

