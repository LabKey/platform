UPDATE core.principals SET type = 'g' WHERE name = 'Developers' AND userid < 0;

SELECT core.executeJavaUpgradeCode('updateDevelopersGroup');

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

CREATE TABLE core.ReportEngineMap
(
  EngineId INTEGER NOT NULL,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_ReportEngineMap PRIMARY KEY (EngineId, Container),
  CONSTRAINT FK_ReportEngineMap_ReportEngines FOREIGN KEY (EngineId) REFERENCES core.ReportEngines (RowId)
);

CREATE TABLE core.PrincipalRelations
(
  userid USERID NOT NULL,
  otherid USERID NOT NULL,
  relationship VARCHAR(100) NULL,
  created TIMESTAMP,

  CONSTRAINT PK_PrincipalRelations PRIMARY KEY (userid, otherid, relationship)
);

SELECT core.fn_dropifexists('PrincipalRelations', 'core', 'TABLE', NULL);

CREATE TABLE core.PrincipalRelations
(
  userid USERID NOT NULL,
  otherid USERID NOT NULL,
  relationship VARCHAR(100) NOT NULL,
  created TIMESTAMP,

  CONSTRAINT PK_PrincipalRelations PRIMARY KEY (userid, otherid, relationship)
);

SELECT core.executeJavaUpgradeCode('migrateDeveloperRole');