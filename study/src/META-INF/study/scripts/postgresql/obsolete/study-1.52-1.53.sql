CREATE TABLE study.UploadLog
(
  RowId SERIAL,
  Container ENTITYID NOT NULL,
  Created TIMESTAMP NOT NULL,
  CreatedBy USERID NOT NULL,
  Description TEXT,
  FilePath VARCHAR(512),
  DatasetId INT NOT NULL,
  Status VARCHAR(20),
  CONSTRAINT PK_UploadLog PRIMARY KEY (RowId),
  CONSTRAINT FK_UploadLog_Dataset FOREIGN KEY (Container,DatasetId) REFERENCES study.Dataset (Container,DatasetId),
  CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath)
)