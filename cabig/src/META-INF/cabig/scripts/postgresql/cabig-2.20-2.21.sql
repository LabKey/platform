select core.fn_dropifexists('ProteinGroupMembers', 'cabig', 'VIEW', NULL);
select core.fn_dropifexists('protsequences', 'cabig', 'VIEW', NULL);

CREATE VIEW cabig.ProtSequences AS
SELECT s.SeqId, s.ProtSequence, s.Hash, s.Description,
	src.Name as SourceName, s.SourceVersion, src.Url as SourceUrl, s.InsertDate, s.OrgId, s.Mass,
	s.BestName, s.BestGeneName, s.Length, o.CommonName as OrganismName, o.Genus, o.Species, o.Comments
FROM prot.Sequences s
	-- join in Source info if available
	LEFT JOIN prot.InfoSources src on (src.SourceId = s.SourceId AND src.Deleted = 0)
	-- join in Org info if is is available
	LEFT JOIN prot.Organisms o ON (s.OrgId = o.OrgId AND o.Deleted = 0)
WHERE s.Deleted = 0
	AND s.SeqId IN (
	SELECT fs.SeqId
	FROM prot.FastaSequences fs
	WHERE fs.FastaId IN (	SELECT FastaId FROM cabig.MS2RunsFilter))
;

CREATE VIEW cabig.ProteinGroupMembers AS
SELECT pgm.SeqId, pgm.ProteinGroupId, pgm.probability, (CAST((4294967296 * pgm.ProteinGroupId) AS BIGINT) + pgm.SeqId) AS ProteinGroupMemberId
FROM ms2.ProteinGroupMemberships pgm
WHERE pgm.ProteinGroupId IN (
	SELECT pg.RowId FROM ms2.ProteinGroups pg
	INNER JOIN ms2.ProteinProphetFiles pp ON pg.ProteinProphetFileId = pp.RowId
	INNER JOIN cabig.MS2RunsFilter r ON r.run = pp.run )
;
