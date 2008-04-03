/*
This update makes the PropertyDescriptor more consistent with OWL terms, and also work for storing NCI_Thesaurus concepts

We're somewhat merging to concepts here.

A PropertyDescriptor with no Domain is a concept (or a Class in OWL).
A PropertyDescriptor with a Domain describes a member of a type (or an ObjectProperty in OWL)
*/

ALTER TABLE exp.PropertyDescriptor DROP COLUMN ValueType
go

EXEC sp_rename @objname = 'exp.PropertyDescriptor.RowId',       @newname = 'PropertyId', @objtype = 'COLUMN'
EXEC sp_rename @objname = 'exp.PropertyDescriptor.TypeURI',     @newname = 'DomainURI', @objtype = 'COLUMN'
EXEC sp_rename @objname = 'exp.PropertyDescriptor.DatatypeURI', @newname = 'RangeURI', @objtype = 'COLUMN'
go

ALTER TABLE exp.PropertyDescriptor ADD ConceptURI nvarchar(200) NULL
ALTER TABLE exp.PropertyDescriptor ADD Label nvarchar(200) NULL
ALTER TABLE exp.PropertyDescriptor ADD SearchTerms nvarchar(1000) NULL
go


DROP VIEW exp.ObjectPropertiesView
go


CREATE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.*,
		PD.name, PD.PropertyURI, PD.RangeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue, P.TextValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId
go


UPDATE exp.PropertyDescriptor
SET ConceptURI = RangeURI, RangeURI = 'http://www.w3.org/2001/XMLSchema#string'
WHERE RangeURI NOT LIKE 'http://www.w3.org/2001/XMLSchema#%'
go


CREATE VIEW exp.ObjectClasses AS
	SELECT DISTINCT DomainURI
	FROM exp.PropertyDescriptor
	WHERE DomainURI IS NOT NULL
go


