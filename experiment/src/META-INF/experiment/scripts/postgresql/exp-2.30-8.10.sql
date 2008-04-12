/* exp-2.30-2.31.sql */

SELECT core.fn_dropifexists('experimentrun', 'cabig','VIEW', NULL);

ALTER TABLE exp.ExperimentRun ALTER COLUMN Name TYPE VARCHAR(100);

/* exp-2.31-2.32.sql */

-- Fix up sample sets that were incorrectly created in a folder other than their domain
-- https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=4976

update exp.materialsource set container = (select min(dd.container) from exp.domaindescriptor dd where exp.materialsource.lsid = dd.domainuri)
where rowid in (select ms.rowid from exp.materialsource ms, exp.domaindescriptor dd where dd.domainuri = ms.lsid and ms.container != dd.container);

/* exp-2.32-2.33.sql */

-- Convert "FileLink" URIs to "Attachment" URIs in Lists
UPDATE exp.PropertyDescriptor SET RangeURI = 'http://www.labkey.org/exp/xml#attachment' WHERE PropertyId IN
(
	SELECT DISTINCT op.PropertyId FROM (SELECT ObjectId FROM exp.IndexInteger UNION SELECT ObjectId FROM exp.IndexVarchar) i INNER JOIN
		exp.ObjectProperty op ON i.ObjectId = op.ObjectId INNER JOIN
		exp.PropertyDescriptor pd ON op.PropertyId = pd.PropertyId
	WHERE RangeURI = 'http://cpas.fhcrc.org/exp/xml#fileLink'
);

/* exp-2.33-2.34.sql */

CREATE INDEX IDX_Material_LSID ON exp.Material(LSID);