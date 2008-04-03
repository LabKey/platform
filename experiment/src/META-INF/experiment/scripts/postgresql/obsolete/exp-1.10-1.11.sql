/*
This update makes the PropertyDescriptor more consistent with OWL terms, and also work for storing NCI_Thesaurus concepts

We're somewhat merging to concepts here.

A PropertyDescriptor with no Domain is a concept (or a Class in OWL).
A PropertyDescriptor with a Domain describes a member of a type (or an ObjectProperty in OWL)
*/

DROP VIEW exp.ObjectPropertiesView;


ALTER TABLE exp.PropertyDescriptor DROP COLUMN ValueType;
ALTER TABLE exp.PropertyDescriptor RENAME COLUMN RowId TO PropertyId;
ALTER TABLE exp.PropertyDescriptor RENAME COLUMN TypeURI TO DomainURI;
ALTER TABLE exp.PropertyDescriptor RENAME COLUMN DatatypeURI TO RangeURI;

ALTER TABLE exp.PropertyDescriptor ADD ConceptURI varchar(200) NULL;
ALTER TABLE exp.PropertyDescriptor ADD Label varchar(200) NULL;
ALTER TABLE exp.PropertyDescriptor ADD SearchTerms varchar(1000) NULL;


CREATE OR REPLACE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.*,
		PD.name, PD.PropertyURI, PD.RangeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue, P.TextValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId
;


CREATE OR REPLACE VIEW exp.ObjectClasses AS
	SELECT DISTINCT DomainURI
	FROM exp.PropertyDescriptor
	WHERE DomainURI IS NOT NULL
;


UPDATE exp.PropertyDescriptor
SET ConceptURI = RangeURI, RangeURI = 'http://www.w3.org/2001/XMLSchema#string'
WHERE RangeURI NOT LIKE 'http://www.w3.org/2001/XMLSchema#%'
;



