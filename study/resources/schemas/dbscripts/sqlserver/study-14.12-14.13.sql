
DROP TABLE study.VisitTag;
CREATE TABLE study.VisitTag
(
  Name NVARCHAR(200) NOT NULL,
  Caption NVARCHAR(200) NOT NULL,
  Description NVARCHAR(MAX),
  SingleUse BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_Name_Container PRIMARY KEY (Name, Container),
);