ALTER TABLE query.QuerySnapshotDef ADD QueryTableContainer ENTITYID
GO

UPDATE query.QuerySnapshotDef SET QueryTableContainer = Container WHERE QueryTableName IS NOT NULL
GO
