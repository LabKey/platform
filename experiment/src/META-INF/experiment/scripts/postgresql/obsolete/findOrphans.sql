/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

CREATE VIEW exp._orphanProtocolView AS
SELECT * FROM exp.Protocol WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE VIEW exp._orphanExperimentView AS
SELECT * FROM exp.Experiment WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE VIEW exp._orphanExperimentRunView AS
SELECT * FROM exp.ExperimentRun WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE VIEW exp._orphanProtocolApplicationView AS
SELECT * FROM exp.ProtocolApplication WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView))
;
CREATE VIEW exp._orphanMaterialView AS
SELECT * FROM exp.Material WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView)) OR 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL)
;
CREATE VIEW exp._orphanDataView AS
SELECT * FROM exp.Data WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView)) OR 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL)
;
CREATE VIEW exp._orphanMaterialSourceView AS
SELECT * FROM exp.MaterialSource WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE VIEW exp._orphanLSIDView AS
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanProtocolView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanExperimentView  UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanExperimentRunView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanMaterialView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanDataView UNION
SELECT LSID, 'n/a' AS Container FROM exp._orphanProtocolApplicationView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanMaterialSourceView 
;
CREATE VIEW exp._orphanObjectView AS
SELECT * FROM exp.Object WHERE ObjectURI IN (SELECT LSID FROM exp._orphanLSIDView) OR 
	container NOT IN (SELECT entityid FROM core.containers)
;
CREATE VIEW exp._orphanPropDescView AS 
SELECT * FROM exp.PropertyDescriptor WHERE container NOT IN (SELECT entityid FROM core.containers)
;


-- general case of all orphans
SELECT 'DataInput' AS TableName, COUNT(*) AS NumOrphans FROM exp.DataInput WHERE 
	(dataid IN (SELECT rowid FROM exp._orphanDataView )) OR 
	(targetapplicationid IN	(SELECT rowid FROM exp._orphanProtocolApplicationView))
UNION
SELECT 'MaterialInput' AS TableName, COUNT(*) AS NumOrphans FROM exp.MaterialInput WHERE 
	(materialid IN (SELECT rowid FROM exp._orphanMaterialView)) OR 
	(targetapplicationid IN	(SELECT rowid FROM exp._orphanProtocolApplicationView))
UNION
SELECT 'Data' AS TableName, COUNT(*) AS NumOrphans FROM exp._orphanDataView 
UNION
SELECT 'Material' AS TableName, COUNT(*) AS NumOrphans FROM exp._orphanMaterialView
UNION
SELECT 'ProtocolApplicationParameter' AS TableName, COUNT(*) AS NumOrphans FROM exp.protocolapplicationparameter WHERE 
	(ProtocolApplicationId IN (SELECT rowid FROM exp._orphanProtocolApplicationView))
UNION
SELECT 'ProtocolApplication' AS TableName, COUNT(*) AS NumOrphans FROM exp._orphanProtocolApplicationView 
UNION
SELECT 'MaterialSource' AS TableName, COUNT(*) AS NumOrphans FROM exp._orphanMaterialSourceView
UNION
SELECT 'ExperimentRun' AS TableName, COUNT(*) AS NumOrphans FROM exp._orphanExperimentRunView 
UNION
SELECT 'ProtocolActionPredecessor' AS TableName, COUNT(*) AS NumOrphans FROM exp.protocolactionpredecessor WHERE 
	(actionid IN (SELECT rowid FROM exp.protocolaction WHERE parentprotocolid IN (SELECT rowid FROM exp._orphanProtocolView)))
UNION
SELECT 'ProtocolAction' AS TableName, COUNT(*) AS NumOrphans FROM exp.protocolaction WHERE 
	(parentprotocolid IN (SELECT rowid FROM exp._orphanProtocolView))
UNION
SELECT 'ProtocolParameter' AS TableName, COUNT(*) AS NumOrphans FROM exp.protocolparameter WHERE 
	(protocolid IN (SELECT rowid FROM exp._orphanProtocolView))
UNION
SELECT 'Protocol' AS TableName, COUNT(*) AS NumOrphans FROM exp._orphanProtocolView
UNION
SELECT 'Experiment' AS TableName, COUNT(*) AS NumOrphans FROM exp._orphanExperimentView 
UNION
SELECT 'ObjectProperty' AS TableName, COUNT(*) AS NumOrphans FROM exp.ObjectProperty WHERE
	(objectid IN (SELECT objectid FROM exp._orphanObjectView) ) OR 
	(propertyid IN (SELECT propertyid FROM exp._orphanPropDescView) ) 
UNION
SELECT 'Object' AS TableName, COUNT(*) AS NumOrphans FROM exp._orphanObjectView
UNION
SELECT 'PropertyDescriptor' AS TableName, COUNT(*) AS NumOrphans FROM exp._orphanPropDescView

ORDER BY 1
;
