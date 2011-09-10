ALTER TABLE query.QuerySnapshotDef ADD COLUMN QueryTableContainer ENTITYID;

UPDATE query.QuerySnapshotDef SET QueryTableContainer = Container WHERE QueryTableName IS NOT NULL;
