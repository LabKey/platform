/* core-17.20-17.21.sql */

CREATE TABLE core.QCState
(
  RowId SERIAL,
  Label VARCHAR(64) NULL,
  Description VARCHAR(500) NULL,
  Container ENTITYID NOT NULL,
  PublicData BOOLEAN NOT NULL,
  CONSTRAINT PK_QCState PRIMARY KEY (RowId),
  CONSTRAINT UQ_QCState_Label UNIQUE(Label, Container)
);