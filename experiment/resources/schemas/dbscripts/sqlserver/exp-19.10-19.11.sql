DROP TABLE exp.Edge;
GO

CREATE TABLE exp.Edge
(
    FromObjectId INT NOT NULL,
--    FromLsid LSIDtype NOT NULL,
    ToObjectId INT NOT NULL,
--    ToLsid LSIDtype NOT NULL,
    RunId INT NOT NULL,

    CONSTRAINT FK_Edge_From_Object FOREIGN KEY (FromObjectId) REFERENCES exp.object (objectid),
    CONSTRAINT FK_Edge_To_Object FOREIGN KEY (ToObjectId) REFERENCES exp.object (objectid),
    CONSTRAINT FK_Edge_RunId_Run FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
-- for query performance
    CONSTRAINT UQ_Edge_FromTo_RunId UNIQUE (FromObjectId, ToObjectId, RunId),
    CONSTRAINT UQ_Edge_ToFrom_RunId UNIQUE (ToObjectId, FromObjectId, RunId)
);
GO

EXEC core.executeJavaUpgradeCode 'rebuildAllEdges';
GO
