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

-- Add ability to drop constraints; switch to $$ quoting for sanity
CREATE OR REPLACE FUNCTION core.fn_dropifexists (text, text, text, text) RETURNS integer AS $$
DECLARE
    objname ALIAS FOR $1;
    objschema ALIAS FOR $2;
    objtype ALIAS FOR $3;
    subobjname ALIAS FOR $4;
	ret_code INTEGER;
	fullname text;
	tempschema text;
    BEGIN
    ret_code := 0;
    fullname := (LOWER(objschema) || '.' || LOWER(objname));
	    IF (UPPER(objtype)) = 'TABLE' THEN
		BEGIN
            IF EXISTS(SELECT * FROM pg_tables WHERE tablename = LOWER(objname) AND schemaname = LOWER(objschema) )
            THEN
                EXECUTE 'DROP TABLE ' || fullname;
                ret_code = 1;
            ELSE
                BEGIN
                    SELECT INTO tempschema schemaname FROM pg_tables WHERE tablename = LOWER(objname) AND schemaname LIKE '%temp%';
                    IF (tempschema IS NOT NULL)
                    THEN
                        EXECUTE 'DROP TABLE ' || tempschema || '.' || objname;
                        ret_code = 1;
                    END IF;
                END;
            END IF;
		END;
	    ELSEIF (UPPER(objtype)) = 'VIEW' THEN
		BEGIN
		    IF EXISTS(SELECT * FROM pg_views WHERE viewname = LOWER(objname) AND schemaname = LOWER(objschema) )
		    THEN
			EXECUTE 'DROP VIEW ' || fullname;
			ret_code = 1;
		    END IF;
		END;
	    ELSEIF (UPPER(objtype)) = 'INDEX' THEN
		BEGIN
		    fullname := LOWER(objschema) || '.' || LOWER(subobjname);
		    IF EXISTS(SELECT * FROM pg_indexes WHERE tablename = LOWER(objname) AND indexname = LOWER(subobjname) AND schemaname = LOWER(objschema) )
		    THEN
			EXECUTE 'DROP INDEX ' || fullname;
			ret_code = 1;
		    ELSE
			IF EXISTS(SELECT * FROM pg_indexes WHERE indexname = LOWER(subobjname) AND schemaname = LOWER(objschema) )
				THEN RAISE EXCEPTION 'INDEX - % defined on a different table.', subobjname;
			END IF;
		    END IF;
		END;
	    ELSEIF (UPPER(objtype)) = 'CONSTRAINT' THEN
		BEGIN
		    IF EXISTS(SELECT * FROM pg_class LEFT JOIN pg_constraint ON conrelid = pg_class.oid INNER JOIN pg_namespace ON pg_namespace.oid = pg_class.relnamespace
                WHERE relkind = 'r' AND contype IS NOT NULL AND nspname = LOWER(objschema) AND relname = LOWER(objname) AND conname = LOWER(subobjname) )
		    THEN
                EXECUTE 'ALTER TABLE ' || fullname || ' DROP CONSTRAINT ' || subobjname;
                ret_code = 1;
		    END IF;
		END;
	    ELSEIF (UPPER(objtype)) = 'SCHEMA' THEN
		BEGIN
		    IF EXISTS(SELECT * FROM pg_namespace WHERE nspname = LOWER(objschema))
		    THEN
			IF objname = '*' THEN
				EXECUTE 'DROP SCHEMA ' || LOWER(objschema) || ' CASCADE';
				ret_code = 1;
			ELSEIF (objname = '' OR objname IS NULL) THEN
				EXECUTE 'DROP SCHEMA ' || LOWER(objschema) || ' RESTRICT';
				ret_code = 1;
			ELSE
				RAISE EXCEPTION 'Invalid objname for objtype of SCHEMA;  must be either "*" (for DROP SCHEMA CASCADE) or NULL (for DROP SCHEMA RESTRICT)';
			END IF;
		    END IF;
		END;
	    ELSE
		RAISE EXCEPTION 'Invalid object type - %;  Valid values are TABLE, VIEW, INDEX, CONSTRAINT, SCHEMA ', objtype;
	    END IF;

	RETURN ret_code;
	END;
$$ LANGUAGE plpgsql;
