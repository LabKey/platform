/* dataintegration-18.10-18.11.sql */

CREATE TABLE dataintegration.EtlDef
(
  EtlDefId SERIAL NOT NULL,
  Container ENTITYID NOT NULL,
  EntityId ENTITYID NOT NULL,
  Name VARCHAR(200) NOT NULL,
  Description VARCHAR,
  Definition VARCHAR NOT NULL,
  Created TIMESTAMP NULL,
  CreatedBy INT NULL,
  Modified TIMESTAMP NULL,
  ModifiedBy INT NULL,

  CONSTRAINT PK_EtlDef PRIMARY KEY (EtlDefId),
  CONSTRAINT UQ_EtlDef UNIQUE (Container, Name),
  CONSTRAINT FK_EtlDef_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);