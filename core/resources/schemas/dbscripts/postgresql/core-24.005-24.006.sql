ALTER TABLE core.Containers ADD FileRootSize BIGINT;
ALTER TABLE core.Containers ADD LastCrawled TIMESTAMP;
ALTER TABLE core.Containers ADD CONSTRAINT PK_Containers PRIMARY KEY (RowId);
ALTER TABLE core.Containers DROP CONSTRAINT UQ_Containers_RowID;