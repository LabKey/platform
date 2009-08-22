/*
 * Copyright (c) 2005-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License")go
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

-- The purpose of this script is to find and delete "orphan" experiment objects, for example
-- ExperimentRuns whose container has been deleted but are still in the database.  Orphans are
-- evidence of a bug in the CPAS system at some point in the past; the current Experiment module 
-- should not be orphaning records anymore.
SET NOCOUNT ON

if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanObjectView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanObjectView 
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanLSIDView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanLSIDView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanMaterialSourceView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanMaterialSourceView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanDataView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanDataView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanMaterialView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanMaterialView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanProtocolApplicationView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanProtocolApplicationView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanExperimentRunView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanExperimentRunView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanExperimentView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanExperimentView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanProtocolView ') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanProtocolView 
GO


if exists (select * from dbo.sysobjects where id = object_id(N'exp.AllLsidContainers ') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.AllLsidContainers
GO
CREATE VIEW exp.AllLsidContainers AS
	SELECT LSID, Container, 'Protocol' AS Type FROM exp.Protocol UNION ALL
	SELECT exp.ProtocolApplication.LSID, Container, 'ProtocolApplication' AS Type FROM exp.ProtocolApplication JOIN exp.Protocol ON exp.Protocol.LSID = exp.ProtocolApplication.ProtocolLSID UNION ALL
	SELECT LSID, Container, 'Experiment' AS Type FROM exp.Experiment UNION ALL
	SELECT LSID, Container, 'Material' AS Type FROM exp.Material UNION ALL
	SELECT LSID, Container, 'MaterialSource' AS Type FROM exp.MaterialSource UNION ALL
	SELECT LSID, Container, 'Data' AS Type FROM exp.Data UNION ALL
	SELECT LSID, Container, 'ExperimentRun' AS Type FROM exp.ExperimentRun
GO


CREATE VIEW exp._orphanProtocolView AS 
SELECT * FROM exp.Protocol WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
go
CREATE VIEW exp._orphanExperimentView AS
SELECT * FROM exp.Experiment WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
go
CREATE VIEW exp._orphanExperimentRunView AS
SELECT * FROM exp.ExperimentRun WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
go
CREATE VIEW exp._orphanProtocolApplicationView AS 
SELECT * FROM exp.ProtocolApplication WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView))
go
CREATE VIEW exp._orphanMaterialView AS
SELECT * FROM exp.Material WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView)) OR 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL)
go
CREATE VIEW exp._orphanDataView AS
SELECT * FROM exp.Data WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView)) OR 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL)
go
CREATE VIEW exp._orphanMaterialSourceView AS 
SELECT * FROM exp.MaterialSource WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
go
CREATE VIEW exp._orphanLSIDView AS 
SELECT LSID, CAST (Container AS nvarchar) AS Container FROM exp._orphanProtocolView UNION
SELECT LSID, CAST (Container AS nvarchar(100)) AS Container FROM exp._orphanExperimentView  UNION
SELECT LSID, CAST (Container AS nvarchar) AS Container FROM exp._orphanExperimentRunView UNION
SELECT LSID, CAST (Container AS nvarchar) AS Container FROM exp._orphanMaterialView UNION
SELECT LSID, CAST (Container AS nvarchar) AS Container FROM exp._orphanDataView UNION
SELECT LSID, 'n/a' AS Container FROM exp._orphanProtocolApplicationView UNION
SELECT LSID, CAST (Container AS nvarchar) AS Container FROM exp._orphanMaterialSourceView 
go

BEGIN TRANSACTION
DELETE FROM exp.DataInput WHERE 
	(dataid IN (SELECT rowid FROM exp._orphanDataView )) OR 
	(targetapplicationid IN	(SELECT rowid FROM exp._orphanProtocolApplicationView))

DELETE FROM exp.MaterialInput WHERE 
	(materialid IN (SELECT rowid FROM exp._orphanMaterialView)) OR 
	(targetapplicationid IN	(SELECT rowid FROM exp._orphanProtocolApplicationView))

DELETE FROM exp.Fraction WHERE 
	(materialid IN (SELECT rowid FROM exp._orphanMaterialView)) 

DELETE FROM exp.biosource WHERE 
	(materialid IN (SELECT rowid FROM exp._orphanMaterialView)) 

DELETE FROM exp.Data WHERE rowid IN (SELECT rowid FROM exp._orphanDataView)

DELETE FROM exp.Material WHERE rowid IN (SELECT rowid FROM exp._orphanMaterialView)

DELETE FROM exp.protocolapplicationparameter WHERE 
	(ProtocolApplicationId IN (SELECT rowid FROM exp._orphanProtocolApplicationView))

DELETE FROM exp.ProtocolApplication WHERE rowid IN (SELECT rowid FROM exp._orphanProtocolApplicationView)

DELETE FROM exp.MaterialSource WHERE rowid IN (SELECT rowid FROM exp._orphanMaterialSourceView)

DELETE FROM exp.ExperimentRun WHERE rowid IN (SELECT rowid FROM exp._orphanExperimentRunView)

DELETE FROM exp.protocolactionpredecessor WHERE 
	(actionid IN (SELECT rowid FROM exp.protocolaction WHERE parentprotocolid IN (SELECT rowid FROM exp._orphanProtocolView)))

DELETE FROM exp.protocolaction WHERE 
	(parentprotocolid IN (SELECT rowid FROM exp._orphanProtocolView))

DELETE FROM exp.protocolparameter WHERE 
	(protocolid IN (SELECT rowid FROM exp._orphanProtocolView))

DELETE FROM exp.Protocol WHERE rowid IN (SELECT rowid FROM exp._orphanProtocolView)

DELETE FROM exp.Experiment WHERE rowid IN (SELECT rowid FROM exp._orphanExperimentView)

DELETE FROM exp.property WHERE 
	(parentURI IN (SELECT LSID FROM exp._orphanLSIDView )) OR 
	(parentURI NOT IN (SELECT lsid FROM exp.AllLsidContainers) 
		AND parentURI NOT IN 
			(SELECT PropertyURIValue FROM exp.Property 
			WHERE parentURI NOT IN (SELECT lsid FROM exp.AllLsidContainers))) 


COMMIT TRANSACTION

go

ALTER TABLE exp.Experiment 
	ALTER COLUMN Container EntityId NOT NULL
go
ALTER TABLE exp.ExperimentRun 
	ALTER COLUMN Container EntityId NOT NULL
go
ALTER TABLE exp.Data
	ALTER COLUMN Container EntityId NOT NULL
go
ALTER TABLE exp.Material
	ALTER COLUMN Container EntityId NOT NULL
go
ALTER TABLE exp.MaterialSource
	ALTER COLUMN Container EntityId NOT NULL
go
ALTER TABLE exp.Protocol
	ALTER COLUMN Container EntityId NOT NULL
go
if exists (select * from dbo.sysobjects where id = object_id(N'exp.AllLsidContainers ') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.AllLsidContainers
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanLSIDView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanLSIDView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanMaterialSourceView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanMaterialSourceView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanDataView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanDataView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanMaterialView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanMaterialView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanProtocolApplicationView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanProtocolApplicationView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanExperimentRunView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanExperimentRunView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanExperimentView') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanExperimentView
GO
if exists (select * from dbo.sysobjects where id = object_id(N'exp._orphanProtocolView ') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp._orphanProtocolView 
GO



