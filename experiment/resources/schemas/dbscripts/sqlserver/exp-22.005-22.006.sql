ALTER TABLE exp.Edge DROP CONSTRAINT UQ_Edge_FromTo_RunId;
ALTER TABLE exp.Edge DROP CONSTRAINT UQ_Edge_ToFrom_RunId;

ALTER TABLE exp.Edge ALTER COLUMN RunId INT NULL;

ALTER TABLE exp.Edge ADD SourceId INT NULL;
ALTER TABLE exp.Edge ADD SourceKey NVARCHAR(200) NULL;

ALTER TABLE exp.Edge ADD CONSTRAINT FK_Edge_SourceId_Object FOREIGN KEY (SourceId) REFERENCES exp.Object (Objectid);
ALTER TABLE exp.Edge ADD CONSTRAINT UQ_Edge_FromTo_RunId_SourceId_SourceKey UNIQUE (FromObjectId, ToObjectId, RunId, SourceId, SourceKey);

CREATE INDEX IX_Edge_ToObjectId ON exp.Edge(ToObjectId);
CREATE INDEX IX_Edge_SourceId ON exp.Edge(SourceId);
GO