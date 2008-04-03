/*
 * Copyright (c) 2007 LabKey Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language verning permissions and
 * limitations under the License.
 */

CREATE VIEW cabig.MS2RunsFilter AS
SELECT mr.run, mr.type, mr.haspeptideprophet, mr.fastaid
	, mr.description as rundescription
	,c.rowid AS containerid
FROM (ms2.runs mr
	INNER JOIN cabig.containers c ON (mr.container = c.entityid))
WHERE mr.deleted = 0 
GO


CREATE VIEW cabig.MS2Runs AS
SELECT mr.run, mr.description, mr.path, mr.filename,
	mr.type, mr.searchengine, mr.massspectype, mr.searchenzyme,
	mr.haspeptideprophet, mr.peptidecount, mr.spectrumcount, mr.negativehitcount,
	mr.fastaid, mr.status, mr.statusid,
	c.rowid AS containerid,
	er.rowid AS experimentrunid
FROM (ms2.runs mr
	INNER JOIN cabig.containers c ON (mr.container = c.entityid))
    	LEFT JOIN exp.experimentrun er ON (er.lsid = mr.experimentrunlsid)
WHERE mr.deleted = 0
GO


CREATE VIEW cabig.Fractions AS
SELECT f.fraction, f.description, f.filename,
    	f.run, f.pepxmldatalsid, f.mzxmlurl
FROM ms2.Fractions f
	INNER JOIN cabig.MS2RunsFilter mr on (f.run = mr.run)
GO


CREATE VIEW cabig.SpectraData AS
SELECT ((4294967296 * CAST(sd.fraction AS BigInt)) + sd.scan) AS spectrumid, f.run, sd.fraction, sd.scan, sd.spectrum
FROM ms2.spectradata sd
	INNER JOIN cabig.Fractions f on (f.Fraction = sd.fraction)
GO

CREATE VIEW cabig.PeptidesView AS
SELECT   frac.run, run.rundescription, pep.fraction
	, LEFT(frac.FileName, CHARINDEX('.', frac.FileName) - 1) AS FractionName
	, pep.scan, pep.retentiontime, pep.charge
	, pep.ionpercent, pep.mass
	, pep.DeltaMass, (pep.Mass + DeltaMass) AS PrecursorMass
	, ABS(DeltaMass - ROUND(DeltaMass, 0)) AS FractionalDeltaMass
	, CASE 
		WHEN pep.Mass = 0 THEN 0 
		ELSE ABS(1000000 * ABS(DeltaMass - ROUND(DeltaMass, 0)) / (pep.Mass + (Charge - 1) * 1.007276)) 
	END AS FractionalDeltaMassPPM
	,CASE 	
		WHEN pep.Mass = 0 THEN 0 
		ELSE ABS(1000000 * DeltaMass / (pep.Mass + (Charge - 1) * 1.007276)) 
	END AS DeltaMassPPM 
        ,CASE 
		WHEN Charge = 0 THEN 0 
		ELSE (pep.Mass + DeltaMass + (Charge - 1) * 1.007276) / Charge 
	END AS MZ
	, pep.peptideprophet, pep.peptidepropheterrorrate, pep.peptide, pep.proteinhits, pep.protein, pep.prevaa, pep.trimmedpeptide
	, pep.nextaa
	, LTRIM(RTRIM(PrevAA + TrimmedPeptide + NextAA)) AS StrippedPeptide
	, pep.sequenceposition, pep.seqid, pep.rowid
	, quant.decimalratio, quant.heavy2lightratio, quant.heavyarea, quant.heavyfirstscan, quant.heavylastscan, quant.heavymass
	, quant.lightarea, quant.lightfirstscan, quant.lightlastscan, quant.lightmass, quant.ratio

	, proph.prophetfval, proph.prophetdeltamass, proph.prophetnumtrypticterm, proph.prophetnummissedcleav

   FROM ms2.peptidesdata pep
   JOIN ms2.fractions frac ON pep.fraction = frac.fraction
   JOIN cabig.MS2RunsFilter run ON frac.run = run.run
   LEFT JOIN ms2.quantitation quant ON pep.rowid = quant.peptideid
   LEFT JOIN ms2.peptideprophetdata proph ON pep.rowid = proph.peptideid

GO


-- issue:  do we need to support XComet specifically?
CREATE VIEW cabig.XTandemScores AS
SELECT pep.rowid as xtandemscoreid
	, pep.score1 AS hyper, pep.score2 AS "next", pep.score3 AS b, pep.score4 AS y, pep.score5 AS expect
FROM ms2.PeptidesData pep	
	INNER JOIN ms2.Fractions f ON (pep.fraction = f.fraction)
	INNER JOIN cabig.MS2RunsFilter r ON (f.run = r.run)
WHERE LOWER(SUBSTRING(r.type, 1, 7))='xtandem'
GO


CREATE VIEW cabig.MascotScores AS
SELECT pep.rowid as mascotscoreid
	, pep.score1 AS ion, pep.score2 AS mascotidentity, pep.score3 AS homology, pep.score5 AS expect
FROM ms2.PeptidesData pep	
	INNER JOIN ms2.Fractions f ON (pep.fraction = f.fraction)
	INNER JOIN cabig.MS2RunsFilter r ON (f.run = r.run)
WHERE LOWER(SUBSTRING(r.type, 1, 6))='mascot'
GO

CREATE VIEW cabig.SequestScores AS
SELECT pep.rowid AS sequestscoreid
	, pep.score1 AS spscore, pep.score2 AS deltacn, pep.score3 AS xcorr, pep.score4 AS sprank
FROM ms2.PeptidesData pep	
	INNER JOIN ms2.Fractions f ON (pep.fraction = f.fraction)
	INNER JOIN cabig.MS2RunsFilter r ON (f.run = r.run)
WHERE LOWER(SUBSTRING(r.type, 1, 7))='sequest'
GO


CREATE VIEW cabig.CometScores AS
SELECT pep.rowid as cometscoreid
	, pep.score1 AS rawscore, pep.score2 AS diffscore, pep.score3 AS zscore
FROM ms2.PeptidesData pep	
	INNER JOIN ms2.Fractions f ON (pep.fraction = f.fraction)
	INNER JOIN cabig.MS2RunsFilter r ON (f.run = r.run)
WHERE LOWER(SUBSTRING(r.type, 1, 5))='comet'
GO


CREATE VIEW cabig.Modifications AS
SELECT CAST(m.run AS BigInt) * 65536 + ASCII(AminoAcid) * 256 + ASCII(Symbol) AS ModificationId,
        m.run, m.aminoacid, m.massdiff, m.variable, m.symbol
FROM ms2.Modifications m 
	INNER JOIN cabig.MS2RunsFilter r ON r.run = m.run
GO

CREATE VIEW cabig.FastaFiles AS
SELECT ff.FastaId, ff.FileName, ff.Loaded, ff.FileChecksum 
FROM prot.FastaFiles ff 
WHERE ff.FastaId IN (SELECT FastaId FROM cabig.MS2RunsFilter)
GO

CREATE VIEW cabig.FastaSequences AS
SELECT fs.FastaId, fs.LookupString, fs.SeqId , 
	(CAST((4294967296 * fs.FastaId) AS BigInt) + fs.seqid) AS FastaSequenceId
FROM prot.FastaSequences fs 
WHERE (fs.FastaId IN (SELECT FastaId FROM cabig.MS2RunsFilter))
GO


CREATE VIEW cabig.QuantSummaries AS
SELECT qs.QuantId, qs.Run, qs.AnalysisType, qs.AnalysisTime, qs.Version, qs.LabeledResidues, 
	qs.MassDiff, qs.MassTol, qs.SameScanRange, qs.XpressLight 
FROM ms2.QuantSummaries qs 
	INNER JOIN cabig.MS2RunsFilter r on (r.run = qs.run)
GO

CREATE VIEW cabig.ProteinProphetFiles AS
SELECT pp.RowId, pp.FilePath, pp.Run, pp.UploadCompleted, pp.MinProbSeries, pp.SensitivitySeries, pp.ErrorSeries, 
	pp.PredictedNumberCorrectSeries, pp.PredictedNumberIncorrectSeries 
FROM ms2.ProteinProphetFiles pp 
	INNER JOIN cabig.MS2RunsFilter r on (r.run = pp.run)
GO


CREATE VIEW cabig.ProteinGroups AS
SELECT pg.RowId, pg.GroupProbability, pg.ProteinProphetFileId, pp.Run, 
	pg.GroupNumber, pg.IndistinguishableCollectionId, 
	pg.UniquePeptidesCount, pg.TotalNumberPeptides, pg.PctSpectrumIds, pg.PercentCoverage, 
	pg.ProteinProbability, pg.ErrorRate
FROM (ms2.ProteinGroups pg 
	INNER JOIN ms2.ProteinProphetFiles pp ON pg.ProteinProphetFileId = pp.RowId 
	INNER JOIN cabig.MS2RunsFilter r ON r.run = pp.run )	
	LEFT JOIN ms2.ProteinQuantitation pq ON pq.ProteinGroupId = pg.RowId
GO

CREATE VIEW cabig.ProteinGroupMembers AS
SELECT pgm.SeqId, pgm.ProteinGroupId, pgm.probability, (CAST((4294967296 * pgm.ProteinGroupId) AS BIGINT) + pgm.SeqId) AS ProteinGroupMemberId
FROM ms2.ProteinGroupMemberships pgm
WHERE pgm.ProteinGroupId IN (
	SELECT pg.RowId FROM ms2.ProteinGroups pg
	INNER JOIN ms2.ProteinProphetFiles pp ON pg.ProteinProphetFileId = pp.RowId 
	INNER JOIN cabig.MS2RunsFilter r ON r.run = pp.run )
GO

CREATE VIEW cabig.PeptideMembers AS
SELECT pm.PeptideId, pm.ProteinGroupId, (CAST((4294967296 * pm.ProteinGroupId) AS BIGINT) + pm.PeptideId) AS PeptideMemberId,
	pm.NSPAdjustedProbability, pm.Weight, pm.NondegenerateEvidence, 
	pm.EnzymaticTermini, pm.SiblingPeptides, pm.SiblingPeptidesBin, pm.Instances, 
	pm.ContributingEvidence, pm.CalcNeutralPepMass 
FROM ms2.PeptideMemberships pm 
WHERE pm.ProteinGroupId IN 
	( SELECT pg.RowId FROM ms2.ProteinGroups pg
	INNER JOIN ms2.ProteinProphetFiles pp ON pg.ProteinProphetFileId = pp.RowId 
	INNER JOIN cabig.MS2RunsFilter r ON r.run = pp.run )

GO

-- the protein sequences visible via cabig are those
-- described in a fasta file used by a run in a published container,
-- and also those that are not marked as deleted

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
GO

exec core.fn_dropifexists 'materialsource', 'cabig','VIEW', NULL
GO

create view cabig.materialsource
as
SELECT ms.rowid, ms.name, ms.lsid, ms.materiallsidprefix, ms.description
	,c.rowid as containerid, dd.domainid
FROM exp.materialsource ms
	INNER JOIN cabig.containers c ON ms.container = c.entityid
	INNER JOIN exp.domaindescriptor dd on (dd.domainuri = ms.lsid)
GO
