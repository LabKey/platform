/*
 * Copyright (c) 2013-2015 LabKey Corporation
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

/* dataintegration-13.10-13.11.sql */

ALTER TABLE dataintegration.TransformRun
  ADD ExpRunId INT;

ALTER TABLE dataintegration.TransformRun
  ADD CONSTRAINT FK_TransformRun_ExpRunId FOREIGN KEY (ExpRunId) REFERENCES exp.ExperimentRun (RowId);

/* dataintegration-13.11-13.12.sql */

DROP PROCEDURE dataintegration.addDataIntegrationColumns;
GO

CREATE PROCEDURE dataintegration.addDataIntegrationColumns @schemaName NVARCHAR(100), @tableName NVARCHAR(100)
AS
DECLARE @sql NVARCHAR(1000)
SELECT @sql = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD  ' +
     '_txRowVersion ROWVERSION, ' +
     '_txModified DATETIME, ' +
     '_txTranformRunId INT, ' +
     '_txNote NVARCHAR(1000)';
EXEC sp_executesql @sql;
SELECT @sql = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD CONSTRAINT [_DF_' + @tableName + '_updated] ' +
    'DEFAULT getutcdate() FOR [_txModified]';
EXEC sp_executesql @sql;
GO

/* dataintegration-13.12-13.13.sql */

DROP PROCEDURE dataintegration.addDataIntegrationColumns;
GO

CREATE PROCEDURE dataintegration.addDataIntegrationColumns @schemaName NVARCHAR(100), @tableName NVARCHAR(100)
AS
DECLARE @sql NVARCHAR(1000)
SELECT @sql = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD  ' +
     '_txRowVersion ROWVERSION, ' +
     '_txModified DATETIME, ' +
     '_txTranformRunId INT, ' +
     '_txNote NVARCHAR(1000)';
EXEC sp_executesql @sql;
SELECT @sql = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD CONSTRAINT [_DF_' + @tableName + '_updated] ' +
    'DEFAULT getutcdate() FOR [_txModified]';
EXEC sp_executesql @sql;
GO

/* dataintegration-13.13-13.14.sql */

EXEC sp_rename
    @objname = 'dataintegration.TransformRun.RowId',
    @newname = 'TransformRunID',
    @objtype = 'COLUMN';
GO

/* dataintegration-13.14-13.15.sql */

DROP PROCEDURE dataintegration.addDataIntegrationColumns;
GO

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

/* dataintegration-13.15-13.16.sql */

ALTER TABLE dataintegration.TransformConfiguration ADD TransformState NTEXT
GO
