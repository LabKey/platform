DROP VIEW exp.ObjectPropertiesView
go

ALTER TABLE exp.ObjectProperty ALTER COLUMN StringValue NVARCHAR(4000) NULL
go

UPDATE exp.ObjectProperty SET StringValue=CAST(TextValue AS NVARCHAR(4000)), TypeTag='s'
WHERE StringValue IS NULL AND (TextValue IS NOT NULL OR TypeTag='t')
go

ALTER TABLE exp.ObjectProperty DROP COLUMN TextValue
go

CREATE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.ObjectId, O.Container, O.ObjectURI, O.OwnerObjectId,
		PD.name, PD.PropertyURI, PD.RangeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId
go
