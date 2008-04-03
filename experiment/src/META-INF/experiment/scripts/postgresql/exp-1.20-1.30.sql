-- index string/float properties
CREATE INDEX IDX_ObjectProperty_FloatValue ON exp.ObjectProperty (PropertyId, FloatValue);
CREATE INDEX IDX_ObjectProperty_StringValue ON exp.ObjectProperty (PropertyId, StringValue);

-- add fk constraints to Data, Material and Object container 

CREATE VIEW exp._noContainerMaterialView AS
SELECT * FROM exp.Material WHERE 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL);
	
CREATE VIEW exp._noContainerDataView AS
SELECT * FROM exp.Data WHERE 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL);
	
CREATE VIEW exp._noContainerObjectView AS 
SELECT * FROM exp.Object WHERE ObjectURI IN 
	(SELECT LSID FROM exp._noContainerMaterialView UNION SELECT LSID FROM exp._noContainerDataView) OR 
	container NOT IN (SELECT entityid FROM core.containers);
	

DELETE FROM exp.ObjectProperty WHERE
	(objectid IN (SELECT objectid FROM exp._noContainerObjectView));
DELETE FROM exp.Object WHERE objectid IN (SELECT objectid FROM exp._noContainerObjectView);
DELETE FROM exp.Data WHERE rowid IN (SELECT rowid FROM exp._noContainerDataView);
DELETE FROM exp.Material WHERE rowid IN (SELECT rowid FROM exp._noContainerMaterialView);

DROP VIEW exp._noContainerObjectView ;
DROP VIEW exp._noContainerDataView;
DROP VIEW exp._noContainerMaterialView;

ALTER TABLE exp.Data ADD CONSTRAINT FK_Data_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.Object ADD CONSTRAINT FK_Object_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
