UPDATE core.principals SET type = 'g' WHERE name = 'Developers' AND userid < 0;

EXEC core.executeJavaUpgradeCode 'updateDevelopersGroup';

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

CREATE TABLE core.ReportEngineMap
(
  EngineId INTEGER NOT NULL,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_ReportEngineMap PRIMARY KEY (EngineId, Container),
  CONSTRAINT FK_ReportEngineMap_ReportEngines FOREIGN KEY (EngineId) REFERENCES core.ReportEngines (RowId)
);

CREATE TABLE core.PrincipalRelations
(
  userid ENTITYID NOT NULL,
  otherid ENTITYID NOT NULL,
  relationship VARCHAR(100) NOT NULL,
  created DATETIME,

  CONSTRAINT PK_PrincipalRelations PRIMARY KEY (userid, otherid, relationship)
);

EXEC core.fn_dropifexists 'PrincipalRelations','core','TABLE';

CREATE TABLE core.PrincipalRelations
(
  userid USERID NOT NULL,
  otherid USERID NOT NULL,
  relationship NVARCHAR(100) NOT NULL,
  created DATETIME,

  CONSTRAINT PK_PrincipalRelations PRIMARY KEY (userid, otherid, relationship)
);

UPDATE core.ReportEngines SET Type = 'External' WHERE TYPE IS NULL;
ALTER TABLE core.ReportEngines DROP CONSTRAINT UQ_Name_Type;
ALTER TABLE core.ReportEngines ALTER COLUMN Type NVARCHAR(64) NOT NULL;
ALTER TABLE core.ReportEngines ADD CONSTRAINT UQ_Name_Type UNIQUE (Name, Type);

EXEC core.executeJavaUpgradeCode 'migrateDeveloperRole';