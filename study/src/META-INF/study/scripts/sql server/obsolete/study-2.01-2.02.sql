ALTER TABLE study.dataset DROP CONSTRAINT UQ_DatasetName
go
ALTER TABLE study.dataset ALTER COLUMN name NVARCHAR(200) NOT NULL
go
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);
go