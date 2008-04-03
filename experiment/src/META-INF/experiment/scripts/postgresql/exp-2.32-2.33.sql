-- Convert "FileLink" URIs to "Attachment" URIs in Lists
UPDATE exp.PropertyDescriptor SET RangeURI = 'http://www.labkey.org/exp/xml#attachment' WHERE PropertyId IN
(
	SELECT DISTINCT op.PropertyId FROM (SELECT ObjectId FROM exp.IndexInteger UNION SELECT ObjectId FROM exp.IndexVarchar) i INNER JOIN
		exp.ObjectProperty op ON i.ObjectId = op.ObjectId INNER JOIN
		exp.PropertyDescriptor pd ON op.PropertyId = pd.PropertyId
	WHERE RangeURI = 'http://cpas.fhcrc.org/exp/xml#fileLink'
);