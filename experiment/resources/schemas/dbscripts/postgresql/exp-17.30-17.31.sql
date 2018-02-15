
CREATE TABLE exp.Edge
(
    FromLsid LSIDTYPE NOT NULL,
    ToLsid LSIDTYPE NOT NULL,
    RunId INT NOT NULL,

    CONSTRAINT FK_Edge_FromLsid_Object FOREIGN KEY (FromLsid) REFERENCES exp.object (objecturi),
    CONSTRAINT FK_Edge_ToLsid_Object FOREIGN KEY (ToLsid) REFERENCES exp.object (objecturi),
    CONSTRAINT FK_Edge_RunId_Run FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
    CONSTRAINT UQ_Edge_FromLsid_ToLsid_RunId UNIQUE (FromLsid, ToLsid, RunId)
);

SELECT core.executeJavaUpgradeCode('rebuildAllEdges');
