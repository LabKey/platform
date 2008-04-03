DROP VIEW exp.ObjectPropertiesView;

ALTER TABLE exp.PropertyDescriptor ALTER COLUMN Name TYPE VARCHAR(200);

-- NOTE: | delimited list of semantic types, could normalize this...
ALTER TABLE exp.PropertyDescriptor ADD COLUMN SemanticType VARCHAR(200);

CREATE OR REPLACE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.*,
		PD.name, PD.PropertyURI, PD.RangeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue, P.TextValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId
;
