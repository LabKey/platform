/* dataintegration-18.10-18.11.sql */

CREATE TABLE dataintegration.EtlDef
(
  EtlDefId INT IDENTITY(1, 1) NOT NULL,
  Container ENTITYID NOT NULL,
  EntityId ENTITYID NOT NULL,
  Name NVARCHAR(200) NOT NULL,
  Description NVARCHAR(MAX),
  Definition NVARCHAR(MAX) NOT NULL,
  Created DATETIME NULL,
  CreatedBy INT NULL,
  Modified DATETIME NULL,
  ModifiedBy INT NULL,

  CONSTRAINT PK_EtlDef PRIMARY KEY (EtlDefId),
  CONSTRAINT UQ_EtlDef UNIQUE (Container, Name),
  CONSTRAINT FK_EtlDef_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);