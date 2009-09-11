/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

IF EXISTS (SELECT * FROM sysobjects WHERE id = object_id('core.fn_dropifexists') AND sysstat & 0xf = 4)
	DROP PROCEDURE core.fn_dropifexists
GO

CREATE PROCEDURE core.fn_dropifexists (@objname varchar(250), @objschema varchar(50), @objtype varchar(50), @subobjname varchar(250)=NULL)
AS
DECLARE	@ret_code INTEGER
DECLARE	@fullname varchar(300)
SELECT @ret_code = 0
SELECT @fullname = (LOWER(@objschema) + '.' + LOWER(@objname))
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
		EXEC('DROP VIEW '+@fullname )
		SELECT @ret_code =1
	END
END
ELSE IF (UPPER(@objtype)) = 'INDEX'
BEGIN
	DECLARE @fullername varchar(500)
	SELECT @fullername = @fullname + '.' + @subobjname
	IF INDEXPROPERTY(OBJECT_ID(@fullname),@subobjname, 'IndexID') IS NOT NULL
	BEGIN

		EXEC('DROP INDEX '+ @fullername )
		SELECT @ret_code =1
	END
	ELSE IF EXISTS (SELECT * FROM sysindexes si INNER JOIN sysobjects so
			ON si.id = so.id
			WHERE si.name = @subobjname
			AND so.name <> @objname)
		RAISERROR ('Index does not belong to specified table ' , 16, 1)
END
ELSE IF (UPPER(@objtype)) = 'SCHEMA'
BEGIN
	DECLARE @uid int
	SELECT @uid=uid FROM sysusers WHERE name = LOWER(@objschema) AND IsAppRole=1
	IF @uid IS NOT NULL
	BEGIN
		IF (@objname = '*' )
		BEGIN
			DECLARE @soName sysname, @parent int, @xt char(2), @fkschema sysname
			DECLARE soCursor CURSOR for SELECT so.name, so.xtype, so.parent_obj, su.name
						FROM sysobjects so
						INNER JOIN sysusers su ON (so.uid = su.uid)
						WHERE (so.uid=@uid)
							OR so.id IN (
								SELECT fso.id FROM sysforeignkeys sfk
								INNER JOIN sysobjects fso ON (sfk.constid = fso.id)
								INNER JOIN sysobjects fsr ON (sfk.rkeyid = fsr.id)
								WHERE fsr.uid=@uid)

						ORDER BY (CASE 	WHEN xtype='V' THEN 1
 								WHEN xtype='P' THEN 2
								WHEN xtype='F' THEN 3
								WHEN xtype='U' THEN 4
							  END)


			OPEN soCursor
			FETCH NEXT FROM soCursor INTO @soName, @xt, @parent, @fkschema
			WHILE @@fetch_status = 0
			BEGIN
				SELECT @fullname = @objschema + '.' + @soName
				IF (@xt = 'V')
					EXEC('DROP VIEW ' + @fullname)
				ELSE IF (@xt = 'P')
					EXEC('DROP PROCEDURE ' + @fullname)
				ELSE IF (@xt = 'F')
				BEGIN
					SELECT @fullname = @fkschema + '.' + OBJECT_NAME(@parent)
					EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @soName)
				END
				ELSE IF (@xt = 'U')
					EXEC('DROP TABLE ' + @fullname)

				FETCH NEXT FROM soCursor INTO @soName, @xt, @parent, @fkschema
			END
			CLOSE soCursor
			DEALLOCATE soCursor

			EXEC sp_dropapprole @objschema

			SELECT @ret_code =1
		END
		ELSE IF (@objname = '' OR @objname IS NULL)
		BEGIN
			EXEC sp_dropapprole @objschema
			SELECT @ret_code =1
		END
		ELSE
			RAISERROR ('Invalid @objname for @objtype of SCHEMA   must be either "*" (to drop all dependent objects) or NULL (for dropping empty schema )' , 16, 1)
	END
END
ELSE
	RAISERROR('Invalid object type - %   Valid values are TABLE, VIEW, INDEX, SCHEMA ', 16,1, @objtype )

RETURN @ret_code
GO

EXEC core.fn_dropifexists 'Containers', 'core', 'Index', 'IX_Containers_Parent_Entity'
GO
EXEC core.fn_dropifexists 'Documents', 'core', 'Index', 'IX_Documents_Container'
GO
EXEC core.fn_dropifexists 'Documents', 'core', 'Index', 'IX_Documents_Parent'
GO
CREATE INDEX IX_Containers_Parent_Entity ON core.Containers(Parent, EntityId)
GO
ALTER TABLE core.Containers ADD CONSTRAINT UQ_Containers_RowId UNIQUE CLUSTERED (RowId)
GO
CREATE INDEX IX_Documents_Container ON core.Documents(Container)
GO
CREATE INDEX IX_Documents_Parent ON core.Documents(Parent)
GO

ALTER TABLE core.Containers ADD
    SortOrder INTEGER NOT NULL DEFAULT 0
GO

ALTER TABLE core.Report
    ADD ReportOwner INT
GO

