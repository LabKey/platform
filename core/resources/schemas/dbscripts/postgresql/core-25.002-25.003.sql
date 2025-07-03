-- persist deferred upgrade methods, potentially across server sessions
CREATE TABLE core.UpgradeSteps
(
    RowId SERIAL,
    ModuleName VARCHAR(255) NOT NULL,
    Script VARCHAR(255) NOT NULL,
    MethodName VARCHAR(255) NOT NULL,
    Created TIMESTAMP NOT NULL,
    Executed TIMESTAMP NULL,

    CONSTRAINT PK_UpgradeSteps PRIMARY KEY (RowId)
);
