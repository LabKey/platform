ALTER TABLE exp.data ADD COLUMN objectid INT;
CREATE UNIQUE INDEX idx_data_objectid ON exp.data (objectid) WHERE objectid IS NOT NULL;
UPDATE exp.data SET objectid = (SELECT objectid FROM exp.object where objecturi=lsid);

ALTER TABLE exp.experimentrun ADD COLUMN objectid INT;
CREATE UNIQUE INDEX idx_experimentrun_objectid ON exp.experimentrun (objectid) WHERE objectid IS NOT NULL;
UPDATE exp.experimentrun SET objectid = (SELECT objectid FROM exp.object where objecturi=lsid);

ALTER TABLE exp.material ADD COLUMN objectid INT;
CREATE UNIQUE INDEX idx_material_objectid ON exp.material (objectid) WHERE objectid IS NOT NULL;
UPDATE exp.material SET objectid = (SELECT objectid FROM exp.object where objecturi=lsid);
