
CREATE TABLE pipeline.TriggerConfigurations
(
  RowId INT IDENTITY(1, 1) NOT NULL,
  Container ENTITYID NOT NULL,
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,

  Name NVARCHAR(255) NOT NULL,
  Description NVARCHAR(MAX),
  Type NVARCHAR(255) NOT NULL,
  Enabled BIT,
  Configuration NVARCHAR(MAX),
  PipelineId NVARCHAR(255) NOT NULL,
  LastChecked DATETIME,

  CONSTRAINT PK_TriggerConfigurations PRIMARY KEY (RowId),
  CONSTRAINT FK_TriggerConfigurations_Container FOREIGN KEY (Container) REFERENCES core.Containers (ENTITYID),
  CONSTRAINT UQ_TriggerConfigurations_Name UNIQUE (Container, Name)
);