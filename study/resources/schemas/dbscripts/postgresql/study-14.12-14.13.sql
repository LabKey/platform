
DROP TABLE study.VisitTag;
CREATE TABLE study.VisitTag
(
  Name VARCHAR(200) NOT NULL,
  Caption VARCHAR(200) NOT NULL,
  Description TEXT,
  SingleUse BOOLEAN NOT NULL DEFAULT false,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_Name_Container PRIMARY KEY (Name, Container)
);