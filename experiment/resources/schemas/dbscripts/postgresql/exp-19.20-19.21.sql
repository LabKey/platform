ALTER TABLE exp.data ADD COLUMN objectid INT;
ALTER TABLE exp.experimentrun ADD COLUMN objectid INT;
ALTER TABLE exp.material ADD COLUMN objectid INT;


INSERT INTO exp.object (objecturi, container)
SELECT D.lsid as objecturi, D.container as container
FROM exp.data D LEFT OUTER JOIN exp.object O ON D.lsid = O.objecturi
WHERE O.objecturi IS NULL;

UPDATE exp.data
SET objectid = (select O.objectid from exp.object O where O.objecturi = lsid);


INSERT INTO exp.object (objecturi, container)
SELECT ER.lsid as objecturi, ER.container as container
FROM exp.experimentrun ER LEFT OUTER JOIN exp.object O ON ER.lsid = O.objecturi
WHERE O.objecturi IS NULL;;

UPDATE exp.experimentrun
SET objectid = (select O.objectid from exp.object O where O.objecturi = lsid);


INSERT INTO exp.object (objecturi, container, ownerobjectid)
SELECT M.lsid as objecturi,M.container as container,
       (select O.objectid from exp.materialsource MS left outer join exp.object O ON MS.lsid = O.objecturi WHERE MS.lsid = M.cpastype) as ownerobjectid
FROM exp.material M LEFT OUTER JOIN exp.object O ON M.lsid = O.objecturi
WHERE O.objecturi IS NULL;;

UPDATE exp.material
SET objectid = (select O.objectid from exp.object O where O.objecturi = lsid);


ALTER TABLE exp.data ALTER COLUMN objectid SET NOT NULL;
ALTER TABLE exp.experimentrun ALTER COLUMN objectid SET NOT NULL;
ALTER TABLE exp.material ALTER COLUMN objectid SET NOT NULL;

CREATE UNIQUE INDEX idx_data_objectid ON exp.data (objectid);
CREATE UNIQUE INDEX idx_experimentrun_objectid ON exp.experimentrun (objectid);
CREATE UNIQUE INDEX idx_material_objectid ON exp.material (objectid);
