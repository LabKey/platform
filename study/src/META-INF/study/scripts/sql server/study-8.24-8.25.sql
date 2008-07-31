CREATE TABLE study.QCState
    (
    RowId INT IDENTITY(1,1),
    Label NVARCHAR(64) NULL,
    Description NVARCHAR(500) NULL,
    Container ENTITYID NOT NULL,
    PublicData BIT NOT NULL,
    CONSTRAINT PK_QCState PRIMARY KEY (RowId),
    CONSTRAINT UQ_QCState_Label UNIQUE(Label, Container)
    )
GO

ALTER TABLE study.StudyData ADD
    QCState INT NULL,
    CONSTRAINT FK_StudyData_QCState FOREIGN KEY (QCState) REFERENCES study.QCState (RowId)

CREATE INDEX IX_StudyData_QCState ON study.StudyData(QCState)
GO

ALTER TABLE study.Study ADD
    DefaultPipelineQCState INT,
    DefaultAssayQCState INT,
    DefaultDirectEntryQCState INT,
    ShowPrivateDataByDefault BIT NOT NULL DEFAULT 0,
    CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES study.QCState (RowId),
    CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES study.QCState (RowId),
    CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES study.QCState (RowId)
GO