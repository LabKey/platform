CREATE TABLE core.ReportEngines
(
  RowId INT IDENTITY(1,1) NOT NULL,
  Name NVARCHAR(255) NOT NULL,
  CreatedBy USERID,
  ModifiedBy USERID,
  Created DATETIME,
  Modified DATETIME,

  Enabled BIT NOT NULL DEFAULT 0,
  Type NVARCHAR(64),
  Description NVARCHAR(255),
  Configuration NVARCHAR(MAX),

  CONSTRAINT PK_ReportEngines PRIMARY KEY (RowId),
  CONSTRAINT UQ_Name_Type UNIQUE (Name, Type)
);

EXEC core.executeJavaUpgradeCode 'migrateEngineConfigurations';
