/*
 * Copyright (c) 2009 LabKey Corporation
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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Migrate batch properties from runs to separate batch objects

-- Create batch rows
INSERT INTO exp.experiment (lsid, name, created, createdby, modified, modifiedby, container, hidden, batchprotocolid)
SELECT
REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(er.lsid, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment'),
er.name + ' Batch', er.created, er.createdby, er.modified, er.modifiedby, er.container, 0, p.rowid FROM exp.experimentrun er, exp.protocol p
WHERE er.lsid LIKE 'urn:lsid:%:%AssayRun.Folder-%:%' AND er.protocollsid = p.lsid
AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL)
GO

-- Add an entry to the object table
INSERT INTO exp.object (objecturi, container)
SELECT
REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(er.lsid, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment'),
er.container FROM exp.experimentrun er
WHERE er.lsid LIKE 'urn:lsid:%:%AssayRun.Folder-%:%'
AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL)
GO

-- Flip the properties to hang from the batch
UPDATE exp.ObjectProperty SET ObjectId =
	(SELECT oBatch.ObjectId
		FROM exp.Object oRun, exp.Object oBatch WHERE exp.ObjectProperty.ObjectId = oRun.ObjectId AND
		REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(oRun.ObjectURI, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment') = oBatch.ObjectURI
	)
WHERE
	PropertyId IN (SELECT dp.PropertyId FROM exp.DomainDescriptor dd, exp.PropertyDomain dp WHERE dd.DomainId = dp.DomainId AND dd.DomainURI LIKE 'urn:lsid:%:AssayDomain-Batch.Folder-%:%')
	AND ObjectId IN (SELECT o.ObjectId FROM exp.Object o, exp.ExperimentRun er WHERE o.ObjectURI = er.LSID AND er.lsid LIKE 'urn:lsid:%:%AssayRun.Folder-%:%'
	AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL))
GO

-- Point the runs at their new batches
INSERT INTO exp.RunList (ExperimentRunId, ExperimentId)
	SELECT er.RowId, e.RowId FROM exp.ExperimentRun er, exp.Experiment e
	WHERE
		e.LSID = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(er.LSID, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment')
		AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL)
GO

-- Clean out the duplicated batch properties on the runs
DELETE FROM exp.ObjectProperty
	WHERE
		ObjectId IN (SELECT o.ObjectId FROM exp.Object o WHERE o.ObjectURI LIKE 'urn:lsid:%:%AssayRun.Folder-%:%')
		AND PropertyId IN (SELECT dp.PropertyId FROM exp.DomainDescriptor dd, exp.PropertyDomain dp WHERE dd.DomainId = dp.DomainId AND dd.DomainURI LIKE 'urn:lsid:%:AssayDomain-Batch.Folder-%:%')
GO
