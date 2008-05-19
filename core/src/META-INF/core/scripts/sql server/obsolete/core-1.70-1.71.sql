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


if exists (select * from sysobjects where id = object_id('core.fn_dropifexists') and sysstat & 0xf = 4)
	drop procedure core.fn_dropifexists
GO

CREATE PROCEDURE core.fn_dropifexists (@objname varchar(250), @objschema varchar(50), @objtype varchar(50), @subobjname varchar(250)=NULL)
AS
DECLARE	@ret_code INTEGER
DECLARE	@fullname varchar(300)
SELECT @ret_code = 0
SELECT @fullname = (lower(@objschema)+'.'+lower(@objname))
IF (upper(@objtype)) = 'TABLE'
BEGIN
	IF OBJECTPROPERTY(OBJECT_ID(@fullname),'IsTable') =1
	BEGIN
		EXEC('DROP TABLE '+@fullname )
		SELECT @ret_code = 1
	END
		ELSE IF @objname LIKE '##%' AND OBJECT_ID('tempdb.dbo.' + @objname) IS NOT NULL
	BEGIN
		EXEC('DROP TABLE '+@objname )
		SELECT @ret_code = 1
	END
END
ELSE IF (upper(@objtype)) = 'VIEW'
BEGIN
	IF OBJECTPROPERTY(OBJECT_ID(@fullname),'IsView') =1
	BEGIN
		EXEC('DROP VIEW '+@fullname )
		SELECT @ret_code =1
	END
END
ELSE IF (upper(@objtype)) = 'INDEX'
BEGIN
	declare @fullername varchar(500)
	select @fullername = @fullname + '.' + @subobjname
	IF INDEXPROPERTY(OBJECT_ID(@fullname),@subobjname, 'IndexID') IS NOT NULL
	BEGIN

		EXEC('DROP INDEX '+ @fullername )
		SELECT @ret_code =1
	END
	ELSE IF EXISTS (SELECT * FROM sysindexes si inner join sysobjects so
			on si.id = so.id
			WHERE si.name = @subobjname
			AND so.name <> @objname)
		RAISERROR ('Index does not belong to specified table ' , 16, 1)
END
ELSE IF (upper(@objtype)) = 'SCHEMA'
BEGIN
	DECLARE @uid int
	SELECT @uid=uid FROM sysusers WHERE name = lower(@objschema) and IsAppRole=1
	IF @uid IS NOT NULL
	BEGIN
		IF (@objname = '*' )
		BEGIN
			DECLARE @soName sysname, @parent int, @xt char(2), @fkschema sysname
			DECLARE soCursor CURSOR for SELECT so.name, so.xtype, so.parent_obj, su.name
						FROM sysobjects so
						INNER JOIN sysusers su on (so.uid = su.uid)
						WHERE (so.uid=@uid)
							OR so.id IN (
								SELECT fso.id from sysforeignkeys sfk
								inner join sysobjects fso on (sfk.constid = fso.id)
								inner join sysobjects fsr on (sfk.rkeyid = fsr.id)
								where fsr.uid=@uid)

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
			RAISERROR ('Invalid @objname for @objtype of SCHEMA   must be either "*" (to drop all dependent objects)  or NULL (for dropping empty schema )' , 16, 1)
	END
END
ELSE
	RAISERROR('Invalid object type - %   Valid values are TABLE, VIEW, INDEX, SCHEMA ', 16,1, @objtype )


RETURN @ret_code
