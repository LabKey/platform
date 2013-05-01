/*
 * Copyright (c) 2013 LabKey Corporation
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
    'DEFAULT getutcdate() FOR [_txLastUpdated]';
EXEC sp_executesql @sql;
GO
