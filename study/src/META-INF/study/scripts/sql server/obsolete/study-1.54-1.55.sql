-- Change visit ids to type numeric:
ALTER TABLE study.Visit
    DROP CONSTRAINT PK_Visit
GO
ALTER TABLE study.Visit
    ALTER COLUMN VisitId NUMERIC(15,4) NOT NULL
GO
ALTER TABLE study.Visit
    ADD CONSTRAINT PK_Visit PRIMARY KEY CLUSTERED (Container,VisitId)
GO


ALTER TABLE study.VisitMap
    DROP CONSTRAINT PK_VisitMap
GO
ALTER TABLE study.VisitMap
    ALTER COLUMN VisitId NUMERIC(15,4) NOT NULL
GO
ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY CLUSTERED (Container,VisitId,DataSetId)
GO


ALTER TABLE study.studydata
    DROP CONSTRAINT AK_ParticipantDataset
GO
ALTER TABLE study.studydata
    ALTER COLUMN VisitId NUMERIC(15,4) NULL
GO
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, VisitId, ParticipantId)
GO

ALTER TABLE study.Specimen
    ALTER COLUMN VisitValue NUMERIC(15,4)
GO