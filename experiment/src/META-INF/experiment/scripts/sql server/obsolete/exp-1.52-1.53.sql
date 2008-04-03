DROP VIEW exp.ObjectPropertiesView
GO

CREATE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.ObjectId, O.Container, O.ObjectURI, O.OwnerObjectId, 
		PD.name, PD.PropertyURI, PD.RangeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue, P.TextValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId
go
