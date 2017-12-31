/*
 * Copyright (c) 2015-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* dataintegration-0.00-13.10.sql */

CREATE SCHEMA dataintegration;

-- CREATE PROCEDURE dataintegration.addDataIntegrationColumns @schemaName NVARCHAR(100), @tableName NVARCHAR(100)
-- AS
-- DECLARE @sql NVARCHAR(1000)
-- SELECT @sql = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD  ' +
--      '_txRowVersion ROWVERSION, ' +
--      '_txLastUpdated DATETIME, ' +
--      '_txTransactionId INT, ' +
--      '_txNote NVARCHAR(1000)';
-- EXEC sp_executesql @sql;
-- SELECT @sql = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD CONSTRAINT [_DF_' + @tableName + '_updated] ' +
--     'DEFAULT getutcdate() FOR [_txLastUpdated]';
-- EXEC sp_executesql @sql;
--
-- GO

CREATE TABLE dataintegration.TransformRun
(
    TransformRunId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    RecordCount INT,
    TransformId VARCHAR(100) NOT NULL,
    TransformVersion INT NOT NULL,
    Status VARCHAR(500),
    StartTime TIMESTAMP NULL,
    EndTime TIMESTAMP NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    JobId INT NULL,
    TransformRunLog TEXT,

    CONSTRAINT PK_TransformRun PRIMARY KEY (TransformRunId),
    CONSTRAINT FK_TransformRun_JobId FOREIGN KEY (JobId) REFERENCES pipeline.StatusFiles (RowId),
    CONSTRAINT FK_TransformRun_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);

CREATE INDEX IDX_TransformRun_Container ON dataintegration.TransformRun(Container);
CREATE INDEX IDX_TransformRun_JobId ON dataintegration.TransformRun(JobId);

CREATE TABLE dataintegration.TransformConfiguration
(
    RowId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    TransformId VARCHAR(100) NOT NULL,
    Enabled BOOLEAN,
    VerboseLogging BOOLEAN,
    LastChecked TIMESTAMP NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    TransformState TEXT,

    CONSTRAINT PK_TransformConfiguration PRIMARY KEY (RowId),
    CONSTRAINT UQ_TransformConfiguration_TransformId UNIQUE (Container, TransformId),
    CONSTRAINT FK_TransformConfiguration_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);

