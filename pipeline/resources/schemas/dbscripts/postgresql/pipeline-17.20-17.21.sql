
CREATE TABLE pipeline.TriggerConfigurations
(
  RowId SERIAL NOT NULL,
  Container ENTITYID NOT NULL,
  Created TIMESTAMP,
  CreatedBy USERID,
  Modified TIMESTAMP,
  ModifiedBy USERID,

  Name VARCHAR(255) NOT NULL,
  Description TEXT,
  Type VARCHAR(255) NOT NULL,
  Enabled BOOLEAN,
  Configuration TEXT,
  PipelineId VARCHAR(255) NOT NULL,
  LastChecked TIMESTAMP,

  CONSTRAINT PK_TriggerConfigurations PRIMARY KEY (RowId),
  CONSTRAINT FK_TriggerConfigurations_Container FOREIGN KEY (Container) REFERENCES core.Containers (ENTITYID),
  CONSTRAINT UQ_TriggerConfigurations_Name UNIQUE (Container, Name)
);
