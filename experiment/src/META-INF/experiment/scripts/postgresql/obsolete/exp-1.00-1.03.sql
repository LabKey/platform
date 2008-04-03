CREATE OR REPLACE VIEW exp._orphanProtocolView AS
SELECT * FROM exp.Protocol WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE OR REPLACE VIEW exp._orphanExperimentView AS
SELECT * FROM exp.Experiment WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE OR REPLACE VIEW exp._orphanExperimentRunView AS
SELECT * FROM exp.ExperimentRun WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE OR REPLACE VIEW exp._orphanProtocolApplicationView AS
SELECT * FROM exp.ProtocolApplication WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView))
;
CREATE OR REPLACE VIEW exp._orphanMaterialView AS
SELECT * FROM exp.Material WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView)) OR 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL)
;
CREATE OR REPLACE VIEW exp._orphanDataView AS
SELECT * FROM exp.Data WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView)) OR 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL)
;
CREATE OR REPLACE VIEW exp._orphanMaterialSourceView AS
SELECT * FROM exp.MaterialSource WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE OR REPLACE VIEW exp._orphanLSIDView AS
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanProtocolView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanExperimentView  UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanExperimentRunView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanMaterialView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanDataView UNION
SELECT LSID, 'n/a' AS Container FROM exp._orphanProtocolApplicationView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanMaterialSourceView 
;
CREATE OR REPLACE VIEW exp.AllLsidContainers AS
	SELECT LSID, Container, 'Protocol' AS Type FROM exp.Protocol UNION ALL
	SELECT exp.ProtocolApplication.LSID, Container, 'ProtocolApplication' AS Type FROM exp.ProtocolApplication JOIN exp.Protocol ON exp.Protocol.LSID = exp.ProtocolApplication.ProtocolLSID UNION ALL
	SELECT LSID, Container, 'Experiment' AS Type FROM exp.Experiment UNION ALL
	SELECT LSID, Container, 'Material' AS Type FROM exp.Material UNION ALL
	SELECT LSID, Container, 'MaterialSource' AS Type FROM exp.MaterialSource UNION ALL
	SELECT LSID, Container, 'Data' AS Type FROM exp.Data UNION ALL
	SELECT LSID, Container, 'ExperimentRun' AS Type FROM exp.ExperimentRun
;



DELETE FROM exp.DataInput WHERE 
	(dataid IN (SELECT rowid FROM exp._orphanDataView )) OR 
	(targetapplicationid IN	(SELECT rowid FROM exp._orphanProtocolApplicationView))
;
DELETE FROM exp.MaterialInput WHERE 
	(materialid IN (SELECT rowid FROM exp._orphanMaterialView)) OR 
	(targetapplicationid IN	(SELECT rowid FROM exp._orphanProtocolApplicationView))
;
DELETE FROM exp.Fraction WHERE 
	(materialid IN (SELECT rowid FROM exp._orphanMaterialView)) 
;
DELETE FROM exp.biosource WHERE 
	(materialid IN (SELECT rowid FROM exp._orphanMaterialView)) 
;
DELETE FROM exp.Data WHERE rowid IN (SELECT rowid FROM exp._orphanDataView)
;
DELETE FROM exp.Material WHERE rowid IN (SELECT rowid FROM exp._orphanMaterialView)
;
DELETE FROM exp.protocolapplicationparameter WHERE 
	(ProtocolApplicationId IN (SELECT rowid FROM exp._orphanProtocolApplicationView))
;
DELETE FROM exp.ProtocolApplication WHERE rowid IN (SELECT rowid FROM exp._orphanProtocolApplicationView)
;
DELETE FROM exp.MaterialSource WHERE rowid IN (SELECT rowid FROM exp._orphanMaterialSourceView)
;
DELETE FROM exp.ExperimentRun WHERE rowid IN (SELECT rowid FROM exp._orphanExperimentRunView)
;
DELETE FROM exp.protocolactionpredecessor WHERE 
	(actionid IN (SELECT rowid FROM exp.protocolaction WHERE parentprotocolid IN (SELECT rowid FROM exp._orphanProtocolView)))
;
DELETE FROM exp.protocolaction WHERE 
	(parentprotocolid IN (SELECT rowid FROM exp._orphanProtocolView))
;
DELETE FROM exp.protocolparameter WHERE 
	(protocolid IN (SELECT rowid FROM exp._orphanProtocolView))
;
DELETE FROM exp.Protocol WHERE rowid IN (SELECT rowid FROM exp._orphanProtocolView)
;
DELETE FROM exp.Experiment WHERE rowid IN (SELECT rowid FROM exp._orphanExperimentView)
;
DELETE FROM exp.property WHERE 
	(parentURI IN (SELECT LSID FROM exp._orphanLSIDView )) OR
	(parentURI NOT IN (SELECT lsid FROM exp.AllLsidContainers)
		AND parentURI NOT IN
			(SELECT PropertyURIValue FROM exp.Property
			WHERE parentURI NOT IN (SELECT lsid FROM exp.AllLsidContainers)))
;

ALTER TABLE exp.Experiment 
	ALTER COLUMN Container SET NOT NULL;
ALTER TABLE exp.ExperimentRun 
	ALTER COLUMN Container SET NOT NULL;
ALTER TABLE exp.Data
	ALTER COLUMN Container SET NOT NULL;
ALTER TABLE exp.Material
	ALTER COLUMN Container SET NOT NULL;
ALTER TABLE exp.MaterialSource
	ALTER COLUMN Container SET NOT NULL;
ALTER TABLE exp.Protocol
	ALTER COLUMN Container SET NOT NULL;

DROP VIEW exp._orphanLSIDView 
;
DROP VIEW exp._orphanMaterialSourceView 
;
DROP VIEW exp._orphanDataView 
;
DROP VIEW exp._orphanMaterialView 
;
DROP VIEW exp._orphanProtocolApplicationView 
;
DROP VIEW exp._orphanExperimentRunView 
;
DROP VIEW exp._orphanExperimentView 
;
DROP VIEW exp._orphanProtocolView 
;
