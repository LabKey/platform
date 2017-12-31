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

/* dataintegration-0.00-14.10.sql */

/* dataintegration-0.00-13.10.sql */

CREATE SCHEMA dataintegration
GO

CREATE TABLE dataintegration.TransformRun
(
    TransformRunId INT IDENTITY (1, 1) NOT NULL,
    Container ENTITYID NOT NULL,
    RecordCount INT,
    TransformId NVARCHAR(100) NOT NULL,
    TransformVersion INT NOT NULL,
    Status NVARCHAR(500),
    StartTime DATETIME NULL,
    EndTime DATETIME NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    JobId INT NULL,
    TransformRunLog NTEXT,

    CONSTRAINT PK_TransformRun PRIMARY KEY (TransformRunId),
    CONSTRAINT FK_TransformRun_JobId FOREIGN KEY (JobId) REFERENCES pipeline.StatusFiles (RowId),
    CONSTRAINT FK_TransformRun_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);

CREATE INDEX IDX_TransformRun_JobId ON dataintegration.TransformRun(JobId);
CREATE INDEX IDX_TransformRun_Container ON dataintegration.TransformRun(Container);

CREATE TABLE dataintegration.TransformConfiguration
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    Container ENTITYID NOT NULL,
    TransformId VARCHAR(100) NOT NULL,
    Enabled BIT,
    VerboseLogging BIT,
    LastChecked DATETIME NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    TransformState NTEXT,

    CONSTRAINT PK_TransformConfiguration PRIMARY KEY (RowId),
    CONSTRAINT UQ_TransformConfiguration_TransformId UNIQUE (Container, TransformId),
    CONSTRAINT FK_TransformConfiguration_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);
GO

/* dataintegration-13.10-13.20.sql */

CREATE PROCEDURE dataintegration.addDataIntegrationColumns @schemaName NVARCHAR(100), @tableName NVARCHAR(100)
AS
BEGIN
    DECLARE @s NVARCHAR(500)

    -- rename if we already have these columns
    IF EXISTS (SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema=@schemaName AND table_name=@tablename AND column_name='_txTranformRunId')
    BEGIN
        SELECT @s = '[' + @schemaName + '].[' + @tableName + '].[_txTranformRunId]'
        EXEC sp_rename @objname=@s, @newname='diTransformRunId', @objtype='COLUMN'
    END
    IF EXISTS (SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema=@schemaName AND table_name=@tablename AND column_name='_txTransformRunId')
    BEGIN
        SELECT @s = '[' + @schemaName + '].[' + @tableName + '].[_txTransformRunId]'
        EXEC sp_rename @objname=@s, @newname='diTransformRunId', @objtype='COLUMN'
    END
    IF EXISTS (SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema=@schemaName AND table_name=@tablename AND column_name='_txRowVersion')
    BEGIN
        SELECT @s = '[' + @schemaName + '].[' + @tableName + '].[_txRowVersion]'
        EXEC sp_rename @objname=@s, @newname='diRowVersion', @objtype='COLUMN'
    END
    IF EXISTS (SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema=@schemaName AND table_name=@tablename AND column_name='_txModified')
    BEGIN
        SELECT @s = '[' + @schemaName + '].[' + @tableName + '].[_txModified]'
        EXEC sp_rename @objname=@s, @newname='diModified', @objtype='COLUMN'
    END
    IF EXISTS (SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema=@schemaName AND table_name=@tablename AND column_name='_txNote')
    BEGIN
        SELECT @s = '[' + @schemaName + '].[' + @tableName + '].[_txNote]'
        EXEC sp_rename @objname=@s, @newname='diNote', @objtype='COLUMN'
    END

    -- create if we don't
    IF NOT EXISTS (SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema=@schemaName AND table_name=@tablename AND column_name='diTransformRunId')
    BEGIN
        SELECT @s = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD diTransformRunId INT'
        EXEC sp_executesql @s
    END
    IF NOT EXISTS (SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema=@schemaName AND table_name=@tablename AND column_name='diRowVersion')
    BEGIN
        SELECT @s = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD diRowVersion ROWVERSION'
        EXEC sp_executesql @s
    END
    IF NOT EXISTS (SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema=@schemaName AND table_name=@tablename AND column_name='diModified')
    BEGIN
        SELECT @s = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD diModified DATETIME'
        EXEC sp_executesql @s
    END
    IF NOT EXISTS (SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema=@schemaName AND table_name=@tablename AND column_name='diNote')
    BEGIN
        SELECT @s = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD diNote NVARCHAR(1000)'
        EXEC sp_executesql @s
    END
END

GO
