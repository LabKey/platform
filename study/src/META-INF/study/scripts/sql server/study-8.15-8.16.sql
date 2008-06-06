UPDATE study.Study
SET DatasetRowsEditable = 0
WHERE
DatasetRowsEditable IS NULL
GO

UPDATE study.Dataset
SET KeyPropertyManaged = 0
WHERE
KeyPropertyManaged IS NULL
GO

ALTER TABLE
study.Study
ALTER COLUMN DatasetRowsEditable BIT NOT NULL
GO

ALTER TABLE
study.Dataset
ALTER COLUMN KeyPropertyManaged BIT NOT NULL
GO
