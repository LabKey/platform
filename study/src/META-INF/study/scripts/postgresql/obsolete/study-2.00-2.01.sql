-- VISITMAP

ALTER TABLE study.visitmap RENAME COLUMN isrequired TO required;


-- VISIT

-- ALTER TABLE study.visit ADD COLUMN name VARCHAR(200);
--
-- UPDATE study.visit SET name=COALESCE(label,cast(rowid as VARCHAR(20)));
-- UPDATE study.visit SET name=rowid
-- WHERE 1 < (SELECT COUNT(*) FROM study.visit V where V.container=study.visit.container and V.name=study.visit.name)
--
-- ALTER TABLE study.visit ALTER name SET NOT NULL;
-- ALTER TABLE study.visit ADD CONSTRAINT UQ_VisitName UNIQUE (container, name);


-- DATASET

ALTER TABLE study.dataset ADD COLUMN name VARCHAR(200);

UPDATE study.dataset SET name=COALESCE(label,cast(datasetid as VARCHAR(20)));
UPDATE study.dataset SET name=datasetid
WHERE 1 < (SELECT COUNT(*) FROM study.dataset D where D.container=study.dataset.container and D.name=study.dataset.name);

ALTER TABLE study.dataset ALTER name SET NOT NULL;
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);
