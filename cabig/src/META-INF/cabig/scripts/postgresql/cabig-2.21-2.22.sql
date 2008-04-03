select core.fn_dropifexists('materialsource', 'cabig','VIEW', NULL);

create or replace view cabig.materialsource
as
SELECT ms.rowid, ms.name, ms.lsid, ms.materiallsidprefix, ms.description
	,c.rowid as containerid, dd.domainid
FROM exp.materialsource ms
	INNER JOIN cabig.containers c ON ms.container = c.entityid
	INNER JOIN exp.domaindescriptor dd on (dd.domainuri = ms.lsid);

select core.fn_dropifexists('ProteinGroupMembers', 'cabig', 'VIEW', NULL);

CREATE VIEW cabig.ProteinGroupMembers AS
SELECT pgm.SeqId, pgm.ProteinGroupId, pgm.probability, (CAST((4294967296 * pgm.ProteinGroupId) AS BIGINT) + pgm.SeqId) AS ProteinGroupMemberId
FROM ms2.ProteinGroupMemberships pgm
WHERE pgm.ProteinGroupId IN (
	SELECT pg.RowId FROM ms2.ProteinGroups pg
	INNER JOIN ms2.ProteinProphetFiles pp ON pg.ProteinProphetFileId = pp.RowId
	INNER JOIN cabig.MS2RunsFilter r ON r.run = pp.run )
;
