CREATE TABLE study.SpecimenComment
       (
       RowId INT IDENTITY(1,1),
       Container ENTITYID NOT NULL,
       SpecimenNumber NVARCHAR(50) NOT NULL,
       GlobalUniqueId NVARCHAR(50) NOT NULL,
       CreatedBy USERID,
       Created DATETIME,
       ModifiedBy USERID,
       Modified DATETIME,
       Comment NTEXT,
       CONSTRAINT PK_SpecimenComment PRIMARY KEY (RowId)
       );

CREATE INDEX IX_SpecimenComment_GlobalUniqueId ON study.SpecimenComment(GlobalUniqueId);
CREATE INDEX IX_SpecimenComment_SpecimenNumber ON study.SpecimenComment(SpecimenNumber);
