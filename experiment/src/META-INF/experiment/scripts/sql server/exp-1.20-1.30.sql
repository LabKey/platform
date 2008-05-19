/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
-- index string/float properties
CREATE INDEX IDX_ObjectProperty_FloatValue ON exp.ObjectProperty (PropertyId, FloatValue)
CREATE INDEX IDX_ObjectProperty_StringValue ON exp.ObjectProperty (PropertyId, StringValue)
go
-- put in constraints to catch orphaned data and materials

CREATE VIEW exp._noContainerMaterialView AS
SELECT * FROM exp.Material WHERE 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL)
go
CREATE VIEW exp._noContainerDataView AS
SELECT * FROM exp.Data WHERE 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL)
go
CREATE VIEW exp._noContainerObjectView AS 
SELECT * FROM exp.Object WHERE ObjectURI IN 
	(SELECT LSID FROM exp._noContainerMaterialView UNION SELECT LSID FROM exp._noContainerDataView) OR 
	container NOT IN (SELECT entityid FROM core.containers)
go

DELETE FROM exp.ObjectProperty WHERE
	(objectid IN (SELECT objectid FROM exp._noContainerObjectView))
DELETE FROM exp.Object WHERE objectid IN (SELECT objectid FROM exp._noContainerObjectView)
DELETE FROM exp.Data WHERE rowid IN (SELECT rowid FROM exp._noContainerDataView)
DELETE FROM exp.Material WHERE rowid IN (SELECT rowid FROM exp._noContainerMaterialView)
go
DROP VIEW exp._noContainerObjectView 
GO
DROP VIEW exp._noContainerDataView
GO
DROP VIEW exp._noContainerMaterialView
GO

ALTER TABLE exp.Data ADD CONSTRAINT FK_Data_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
go
ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
go
ALTER TABLE exp.Object ADD CONSTRAINT FK_Object_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
go

	