/* exp-17.30-17.31.sql */

CREATE TABLE exp.Edge
(
  FromLsid LSIDtype NOT NULL,
  ToLsid LSIDtype NOT NULL,
  RunId INT NOT NULL,

  CONSTRAINT FK_Edge_FromLsid_Object FOREIGN KEY (FromLsid) REFERENCES exp.object (objecturi),
  CONSTRAINT FK_Edge_ToLsid_Object FOREIGN KEY (ToLsid) REFERENCES exp.object (objecturi),
  CONSTRAINT FK_Edge_RunId_Run FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
  CONSTRAINT UQ_Edge_FromLsid_ToLsid_RunId UNIQUE (FromLsid, ToLsid, RunId)
);

EXEC core.executeJavaUpgradeCode 'rebuildAllEdges';