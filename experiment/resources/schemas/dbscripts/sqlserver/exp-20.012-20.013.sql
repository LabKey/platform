-- create exp.object for exp.data without one
INSERT INTO exp.object (objecturi, container)
SELECT D.lsid as objecturi, D.container as container
FROM exp.data D LEFT OUTER JOIN exp.object O ON D.lsid = O.objecturi
WHERE O.objecturi IS NULL;

-- fixup exp.data.objectid to be consistent with the lsid of the exp.object
UPDATE exp.data
SET objectid = (SELECT O.objectid FROM exp.object O WHERE O.objecturi = lsid)
WHERE rowid IN (
    SELECT rowid
    FROM exp.data D
    LEFT OUTER JOIN exp.object O ON D.lsid = O.objecturi
    WHERE D.objectid != O.objectid
);

-- add constraints for lsid -> exp.object
ALTER TABLE exp.data ADD CONSTRAINT FK_Data_Lsid
    FOREIGN KEY (lsid) REFERENCES exp.object (objecturi);
ALTER TABLE exp.material ADD CONSTRAINT FK_Material_Lsid
    FOREIGN KEY (lsid) REFERENCES exp.object (objecturi);
ALTER TABLE exp.experimentrun ADD CONSTRAINT FK_ExperimentRun_Lsid
    FOREIGN KEY (lsid) REFERENCES exp.object (objecturi);

-- add constraints for objectid -> exp.object
ALTER TABLE exp.data ADD CONSTRAINT FK_Data_ObjectId
    FOREIGN KEY (objectid) REFERENCES exp.object (objectid);
ALTER TABLE exp.material ADD CONSTRAINT FK_Material_ObjectId
    FOREIGN KEY (objectid) REFERENCES exp.object (objectid);
ALTER TABLE exp.experimentrun ADD CONSTRAINT FK_ExperimentRun_ObjectId
    FOREIGN KEY (objectid) REFERENCES exp.object (objectid);
GO
