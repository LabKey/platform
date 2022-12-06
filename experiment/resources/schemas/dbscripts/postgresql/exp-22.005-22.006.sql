ALTER TABLE exp.Edge
    DROP CONSTRAINT UQ_Edge_FromTo_RunId,
    DROP CONSTRAINT UQ_Edge_ToFrom_RunId,

    ALTER COLUMN RunId DROP NOT NULL,

    ADD SourceId INT NULL,
    ADD SourceKey VARCHAR(200) NULL,

    ADD CONSTRAINT FK_Edge_SourceId_Object FOREIGN KEY (SourceId) REFERENCES exp.Object (Objectid),
    ADD CONSTRAINT UQ_Edge_FromTo_RunId_SourceId_SourceKey UNIQUE (FromObjectId, ToObjectId, RunId, SourceId, SourceKey);

CREATE INDEX IX_Edge_ToObjectId ON exp.Edge(ToObjectId);
CREATE INDEX IX_Edge_SourceId ON exp.Edge(SourceId);
