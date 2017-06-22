/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
/* core-16.30-16.31.sql */

ALTER TABLE core.UsersData ADD ExpirationDate DATETIME;

GO

/* core-16.31-16.32.sql */

-- Add ability to drop columns
ALTER PROCEDURE [core].[fn_dropifexists] (@objname VARCHAR(250), @objschema VARCHAR(50), @objtype VARCHAR(50), @subobjname VARCHAR(250) = NULL, @printCmds BIT = 0)
AS
BEGIN
  /*
    Procedure to safely drop most database object types without error if the object does not exist. Schema deletion
     will cascade to the tables and programability objects in that schema. Column deletion will cascade to any keys,
     constraints, and indexes on the column.
       Usage:
       EXEC core.fn_dropifexists objname, objschema, objtype, subobjname, printCmds
       where:
       objname    Required. For TABLE, VIEW, PROCEDURE, FUNCTION, AGGREGATE, SYNONYM, this is the name of the object to be dropped
                   for SCHEMA, specify '*' to drop all dependent objects, or NULL to drop an empty schema
                   for INDEX, CONSTRAINT, DEFAULT, or COLUMN, specify the name of the table
       objschema  Requried. The name of the schema for the object, or the schema being dropped
       objtype    Required. The type of object being dropped. Valid values are TABLE, VIEW, INDEX, CONSTRAINT, DEFAULT, SCHEMA, PROCEDURE, FUNCTION, AGGREGATE, SYNONYM, COLUMN
       subobjtype Optional. When dropping INDEX, CONSTRAINT, DEFAULT, or COLUMN, the name of the object being dropped
       printCmds  Optional, 1 or 0. If 1, the cascading drop commands for SCHEMA and COLUMN will be printed for debugging purposes
   */
  DECLARE @ret_code INTEGER
  DECLARE @fullname VARCHAR(500)
  DECLARE @fkConstName sysname, @fkTableName sysname, @fkSchema sysname
  SELECT @ret_code = 0
  SELECT @fullname = (@objschema + '.' + @objname)
  IF (UPPER(@objtype)) = 'TABLE'
    BEGIN
      IF OBJECTPROPERTY(OBJECT_ID(@fullname), 'IsTable') =1
        BEGIN
          EXEC('DROP TABLE ' + @fullname )
          SELECT @ret_code = 1
        END
      ELSE IF @objname LIKE '##%' AND OBJECT_ID('tempdb.dbo.' + @objname) IS NOT NULL
        BEGIN
          EXEC('DROP TABLE ' + @objname )
          SELECT @ret_code = 1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'VIEW'
    BEGIN
      IF OBJECTPROPERTY(OBJECT_ID(@fullname),'IsView') =1
        BEGIN
          EXEC('DROP VIEW ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'INDEX'
    BEGIN
      DECLARE @fullername VARCHAR(500)
      SELECT @fullername = @fullname + '.' + @subobjname
      IF INDEXPROPERTY(OBJECT_ID(@fullname), @subobjname, 'IndexID') IS NOT NULL
        BEGIN
          EXEC('DROP INDEX ' + @fullername )
          SELECT @ret_code =1
        END
      ELSE IF EXISTS (SELECT * FROM sys.indexes si
      WHERE si.name = @subobjname
            AND OBJECT_NAME(si.object_id) <> @objname)
        BEGIN
          RAISERROR ('Index does not belong to specified table ' , 16, 1)
          RETURN @ret_code
        END
    END
  ELSE IF (UPPER(@objtype)) = 'CONSTRAINT'
    BEGIN
      IF OBJECTPROPERTY(OBJECT_ID(@objschema + '.' + @subobjname), 'IsConstraint') = 1
        BEGIN
          EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @subobjname)
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'DEFAULT'
    BEGIN
      DECLARE @DEFAULT sysname
      SELECT 	@DEFAULT = s.name
      FROM sys.objects s
        join sys.columns c ON s.object_id = c.default_object_id
      WHERE
        s.type = 'D'
        and c.object_id = OBJECT_ID(@fullname)
        and c.name = @subobjname

      IF @DEFAULT IS NOT NULL AND OBJECTPROPERTY(OBJECT_ID(@objschema + '.' + @DEFAULT), 'IsConstraint') = 1
        BEGIN
          EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @DEFAULT)
          if (@printCmds = 1) PRINT('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @DEFAULT)
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'SCHEMA'
    BEGIN
      DECLARE @schemaid INT, @principalid int

      SELECT @schemaid=schema_id, @principalid=principal_id
      FROM sys.schemas
      WHERE name = @objschema

      IF @schemaid IS NOT NULL
        BEGIN
          IF (@objname is NOT NULL AND @objname NOT IN ('', '*'))
            BEGIN
              RAISERROR ('Invalid @objname for @objtype of SCHEMA   must be either "*" (to drop all dependent objects) or NULL (for dropping empty schema )' , 16, 1)
              RETURN @ret_code
            END
          ELSE IF (@objname = '*' )
            BEGIN
              DECLARE fkCursor CURSOR LOCAL for
                SELECT object_name(sfk.object_id) as fk_constraint_name, object_name(sfk.parent_object_id) as fk_table_name,
                       schema_name(sfk.schema_id) as fk_schema_name
                FROM sys.foreign_keys sfk
                  INNER JOIN sys.objects fso ON (sfk.referenced_object_id = fso.object_id)
                WHERE fso.schema_id=@schemaid
                      AND sfk.type = 'F'

              OPEN fkCursor
              FETCH NEXT FROM fkCursor INTO @fkConstName, @fkTableName, @fkSchema
              WHILE @@fetch_status = 0
                BEGIN
                  SELECT @fullname = @fkSchema + '.' +@fkTableName
                  EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @fkConstName)
                  if (@printCmds = 1) PRINT('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @fkConstName)

                  FETCH NEXT FROM fkCursor INTO @fkConstName, @fkTableName, @fkSchema
                END
              CLOSE fkCursor
              DEALLOCATE fkCursor

              DECLARE @soName sysname, @parent INT, @type CHAR(2), @fkschemaid int
              DECLARE soCursor CURSOR LOCAL for
                SELECT so.name, so.type, so.parent_object_id, so.schema_id
                FROM sys.objects so
                WHERE (so.schema_id=@schemaid)
                ORDER BY (CASE  WHEN so.type='V' THEN 1
                          WHEN so.type='P' THEN 2
                          WHEN so.type IN ('FN', 'IF', 'TF', 'FS', 'FT') THEN 3
                          WHEN so.type='AF' THEN 4
                          WHEN so.type='U' THEN 5
                          WHEN so.type='SN' THEN 6
                          ELSE 7
                          END)
              OPEN soCursor
              FETCH NEXT FROM soCursor INTO @soName, @type, @parent, @fkschemaid
              WHILE @@fetch_status = 0
                BEGIN
                  SELECT @fullname = @objschema + '.' + @soName
                  IF (@type = 'V')
                    BEGIN
                      EXEC('DROP VIEW ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP VIEW ' + @fullname)
                    END
                  ELSE IF (@type = 'P')
                    BEGIN
                      EXEC('DROP PROCEDURE ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP PROCEDURE ' + @fullname)
                    END
                  ELSE IF (@type IN ('FN', 'IF', 'TF', 'FS', 'FT'))
                    BEGIN
                      EXEC('DROP FUNCTION ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP FUNCTION ' + @fullname)
                    END
                  ELSE IF (@type = 'AF')
                    BEGIN
                      EXEC('DROP AGGREGATE ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP AGGREGATE ' + @fullname)
                    END
                  ELSE IF (@type = 'U')
                    BEGIN
                      EXEC('DROP TABLE ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP TABLE ' + @fullname)
                    END
                  ELSE IF (@type = 'SN')
                    BEGIN
                      EXEC('DROP SYNONYM ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP SYNONYM ' + @fullname)
                    END
                  ELSE
                    BEGIN
                      DECLARE @msg NVARCHAR(255)
                      SELECT @msg=' Found object of type: ' + @type + ' name: ' + @fullname + ' in this schema.  Schema not dropped. '
                      RAISERROR (@msg, 16, 1)
                      RETURN @ret_code
                    END
                  FETCH NEXT FROM soCursor INTO @soName, @type, @parent, @fkschemaid
                END
              CLOSE soCursor
              DEALLOCATE soCursor
            END

          IF (@objSchema != 'dbo')
            BEGIN
              DECLARE @approlename sysname
              SELECT @approlename = name
              FROM sys.database_principals
              WHERE principal_id=@principalid AND type='A'

              IF (@approlename IS NOT NULL)
                BEGIN
                  EXEC sp_dropapprole @approlename
                  if (@printCmds = 1) PRINT ('sp_dropapprole '+ @approlename)
                END
              ELSE
                BEGIN
                  EXEC('DROP SCHEMA ' + @objschema)
                  if (@printCmds = 1) PRINT('DROP SCHEMA ' + @objschema)
                END
            END
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'PROCEDURE'
    BEGIN
      IF (@objschema = 'sys')
        BEGIN
          RAISERROR ('Invalid @objschema, not attempting to drop sys object', 16, 1)
          RETURN @ret_code
        END
      IF OBJECTPROPERTY(OBJECT_ID(@fullname),'IsProcedure') =1
        BEGIN
          EXEC('DROP PROCEDURE ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'FUNCTION'
    BEGIN
      IF EXISTS (SELECT 1 FROM sys.objects o JOIN sys.schemas s ON o.schema_id = s.schema_id WHERE s.name = @objschema AND o.name = @objname AND o.type IN ('FN', 'IF', 'TF', 'FS', 'FT'))
        BEGIN
          EXEC('DROP FUNCTION ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'AGGREGATE'
    BEGIN
      IF EXISTS (SELECT 1 FROM sys.objects o JOIN sys.schemas s ON o.schema_id = s.schema_id WHERE s.name = @objschema AND o.name = @objname AND o.type = 'AF')
        BEGIN
          EXEC('DROP AGGREGATE ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'SYNONYM'
    BEGIN
      IF EXISTS (SELECT 1 FROM sys.objects o JOIN sys.schemas s ON o.schema_id = s.schema_id WHERE s.name = @objschema AND o.name = @objname AND o.type = 'SN')
        BEGIN
          EXEC('DROP SYNONYM ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'COLUMN'
    BEGIN
      DECLARE @tableID		INT
      SET @tableID = OBJECT_ID(@fullname)
      IF EXISTS (SELECT 1 FROM sys.columns WHERE Name = N'' + @subobjname AND Object_ID = @tableID)
        BEGIN
          -- Drop any indexes and constraints on the column
          DECLARE @index          SYSNAME
          DECLARE cur_indexes CURSOR FOR
            SELECT
              i.Name
            FROM
              sys.indexes i
              INNER JOIN
              sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
              INNER JOIN
              sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE
              c.name = @subobjname
              AND is_primary_key = 0
              AND i.object_id = @tableID

          DECLARE fkCursor CURSOR LOCAL for
            SELECT object_name(fk.object_id), object_name(fkc.parent_object_id), schema_name(fk.schema_id)
            FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
              JOIN sys.columns c ON fkc.referenced_object_id = c.object_id AND c.column_id = fkc.referenced_column_id
            WHERE fk.referenced_object_id = @tableID AND c.name = @subobjname

          BEGIN TRANSACTION
          BEGIN TRY
          -- Drop indexes
          OPEN cur_indexes
          FETCH NEXT FROM cur_indexes INTO @index
          WHILE (@@FETCH_STATUS = 0)
            BEGIN
              if (@printCmds = 1) PRINT('DROP INDEX ' + @index + ' ON ' + @fullname + '-- index drop')
              EXEC('DROP INDEX ' + @index + ' ON ' + @fullname)
              FETCH NEXT FROM cur_indexes INTO @index
            END
          CLOSE cur_indexes

          -- Drop foreign keys
          DECLARE @fkFullName sysname
          OPEN fkCursor
          FETCH NEXT FROM fkCursor INTO @fkConstName, @fkTableName, @fkSchema
          WHILE @@fetch_status = 0
            BEGIN
              SELECT @fkFullName = @fkSchema + '.' +@fkTableName
              if (@printCmds = 1) PRINT('ALTER TABLE ' + @fkFullName + ' DROP CONSTRAINT ' + @fkConstName + ' -- FK drop')
              EXEC('ALTER TABLE ' + @fkFullname + ' DROP CONSTRAINT ' + @fkConstName)
              FETCH NEXT FROM fkCursor INTO @fkConstName, @fkTableName, @fkSchema
            END
          CLOSE fkCursor

          -- Drop default constraint on column
          DECLARE @ConstraintName nvarchar(200)
          SELECT @ConstraintName = Name FROM SYS.DEFAULT_CONSTRAINTS WHERE PARENT_OBJECT_ID = @tableID AND PARENT_COLUMN_ID = (SELECT column_id FROM sys.columns WHERE NAME = N'' + @subobjname AND object_id = @tableID)
          IF @ConstraintName IS NOT NULL
            BEGIN
              if (@printCmds = 1) PRINT ('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @ConstraintName + ' -- default constraint drop')
              EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @ConstraintName)
            END

          -- Drop other constraints, including PK
          SET @ConstraintName = NULL
          while 0=0 begin
            set @constraintName = (
              select top 1 constraint_name
              from information_schema.constraint_column_usage
              where TABLE_SCHEMA = @objschema and table_name = @objname and column_name = @subobjname )
            if @constraintName is null break
            if (@printCmds = 1) PRINT ('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @ConstraintName + ' -- other constraint drop')
            exec ('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @ConstraintName)
          end

          -- Now drop the column
          if (@printCmds = 1) PRINT ('ALTER TABLE ' + @fullname + ' DROP COLUMN ' + @subobjname )
          EXEC('ALTER TABLE ' + @fullname + ' DROP COLUMN ' + @subobjname )
          SELECT @ret_code =1

          DEALLOCATE cur_indexes
          DEALLOCATE fkCursor
          COMMIT TRANSACTION
          END TRY
          BEGIN CATCH
          ROLLBACK TRANSACTION
          DEALLOCATE cur_indexes
          DEALLOCATE fkCursor

          DECLARE @error varchar(max)
          SET @error = 'Error dropping column %s. The column has not been changed. This procedure can automatically drop indexes, foreign keys or primary keys, defaults, and other constraints on a column, but not other objects such as triggers or rules.
			Original error from SQL Server was: ' + ERROR_MESSAGE()
          RAISERROR(@error, 16, 1, @subobjname)
          END CATCH
        END
    END
  ELSE
    RAISERROR('Invalid object type - %s   Valid values are TABLE, VIEW, INDEX, CONSTRAINT, DEFAULT, SCHEMA, PROCEDURE, FUNCTION, AGGREGATE, SYNONYM, COLUMN', 16,1, @objtype )

  RETURN @ret_code;
END

GO