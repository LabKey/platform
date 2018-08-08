CREATE TABLE core.ReportEngines
(
  RowId SERIAL,
  Name VARCHAR(255) NOT NULL,
  CreatedBy USERID,
  ModifiedBy USERID,
  Created TIMESTAMP,
  Modified TIMESTAMP,

  Enabled BOOLEAN NOT NULL DEFAULT FALSE,
  Type VARCHAR(64) NOT NULL,
  Description VARCHAR(255),
  Configuration TEXT,

  CONSTRAINT PK_ReportEngines PRIMARY KEY (RowId),
  CONSTRAINT UQ_Name_Type UNIQUE (Name, Type)
);

SELECT core.executeJavaUpgradeCode('migrateEngineConfigurations');

