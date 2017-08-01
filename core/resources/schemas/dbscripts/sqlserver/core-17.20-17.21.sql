
CREATE TABLE core.QCState
(
  RowId INT IDENTITY(1,1),
  Label NVARCHAR(64) NULL,
  Description NVARCHAR(500) NULL,
  Container ENTITYID NOT NULL,
  PublicData BIT NOT NULL,

  CONSTRAINT PK_QCState PRIMARY KEY (RowId),
  CONSTRAINT UQ_QCState_Label UNIQUE(Label, Container)
);