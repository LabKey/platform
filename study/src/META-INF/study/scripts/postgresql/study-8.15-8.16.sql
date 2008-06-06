UPDATE study.Study
SET DatasetRowsEditable = FALSE
WHERE
DatasetRowsEditable IS NULL;

UPDATE study.Dataset
SET KeyPropertyManaged = FALSE
WHERE
KeyPropertyManaged IS NULL;

ALTER TABLE study.Study
    ALTER COLUMN DatasetRowsEditable SET NOT NULL;

ALTER TABLE study.Dataset
    ALTER COLUMN KeyPropertyManaged SET NOT NULL;
