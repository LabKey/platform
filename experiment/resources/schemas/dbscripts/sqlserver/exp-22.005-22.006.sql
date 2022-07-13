ALTER TABLE exp.Edge DROP CONSTRAINT UQ_Edge_FromTo_RunId;
ALTER TABLE exp.Edge DROP CONSTRAINT UQ_Edge_ToFrom_RunId;

ALTER TABLE exp.Edge ALTER COLUMN RunId INT NULL;

ALTER TABLE exp.Edge ADD SourceId INT NULL;
ALTER TABLE exp.Edge ADD SourceKey NVARCHAR(200) NULL;

ALTER TABLE exp.Edge ADD CONSTRAINT FK_Edge_SourceId_Object FOREIGN KEY (SourceId) REFERENCES exp.Object (Objectid);
ALTER TABLE exp.Edge ADD CONSTRAINT UQ_Edge_FromTo_RunId_SourceIdSourceKey UNIQUE (FromObjectId, ToObjectId, RunId, SourceId, SourceKey);
ALTER TABLE exp.Edge ADD CONSTRAINT UQ_Edge_ToFrom_RunId_SourceIdSourceKey UNIQUE (ToObjectId, FromObjectId, RunId, SourceId, SourceKey);
GO