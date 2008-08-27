CREATE TABLE study.SpecimenComment
       (
       RowId SERIAL,
       Container ENTITYID NOT NULL,
       SpecimenNumber VARCHAR(50) NOT NULL,
       GlobalUniqueId VARCHAR(50) NOT NULL,
       CreatedBy USERID,
       Created TIMESTAMP,
       ModifiedBy USERID,
       Modified TIMESTAMP,
       Comment TEXT,
       CONSTRAINT PK_SpecimenComment PRIMARY KEY (RowId)
       );

CREATE INDEX IX_SpecimenComment_GlobalUniqueId ON study.SpecimenComment(GlobalUniqueId);
CREATE INDEX IX_SpecimenComment_SpecimenNumber ON study.SpecimenComment(SpecimenNumber);
