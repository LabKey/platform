DROP VIEW exp.ObjectPropertiesView
;

ALTER TABLE exp.ObjectProperty ALTER COLUMN StringValue TYPE VARCHAR(4000)
;

UPDATE exp.ObjectProperty SET StringValue=CAST(TextValue AS VARCHAR(4000)), TypeTag='s'
WHERE StringValue IS NULL AND (TextValue IS NOT NULL OR TypeTag='t')
;

ALTER TABLE exp.ObjectProperty DROP COLUMN TextValue
;

CREATE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.ObjectId, O.Container, O.ObjectURI, O.OwnerObjectId,
		PD.name, PD.PropertyURI, PD.RangeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId
;


