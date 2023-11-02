CREATE INDEX IDX_ExperimentRun_WorkflowTask ON exp.ExperimentRun(WorkflowTask);
CREATE INDEX IDX_Edge_RunId ON exp.Edge(RunId);

-- Add new "RootMaterialRowId" column
ALTER TABLE exp.material ADD rootmaterialrowid INTEGER NULL;
GO

-- Update all exp.material rows to have a "RootMaterialRowId"
UPDATE Material
    SET Material.rootmaterialrowid = Parent.rowid
    FROM exp.material Material
    INNER JOIN exp.material Parent ON Material.rootmateriallsid = Parent.lsid;
GO

-- Add NOT NULL constraint to "RootMaterialRowId"
ALTER TABLE exp.material ALTER COLUMN rootmaterialrowid INTEGER NOT NULL;
GO

CREATE INDEX ix_material_rootrowid ON exp.material (rootmaterialrowid);
GO

-- Remove the "RootMaterialLSID" column
EXEC core.fn_dropifexists 'material', 'exp', 'INDEX', 'uq_material_rootlsid';
EXEC core.fn_dropifexists 'material', 'exp', 'COLUMN', 'rootmateriallsid';

EXEC core.executeJavaUpgradeCode 'addRowIdToMaterializedSampleTypes';
