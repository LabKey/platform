CREATE TABLE study.StudyDesignChallengeTypes
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignchallengetypes PRIMARY KEY (Container, Name)
);