/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

ALTER TABLE core.Containers
  ADD ExperimentID INT,
  ADD Description VARCHAR(4000);

-- experiment id should not be on Containers table
ALTER TABLE core.Containers
  DROP COLUMN ExperimentID;

ALTER TABLE core.Containers
  ADD Workbook BOOLEAN NOT NULL DEFAULT false;

-- Add ability to drop default
CREATE OR REPLACE FUNCTION core.fn_dropifexists (text, text, text, text) RETURNS INTEGER AS $$
DECLARE
    objname ALIAS FOR $1;
    objschema ALIAS FOR $2;
    objtype ALIAS FOR $3;
    subobjname ALIAS FOR $4;
    ret_code INTEGER;
    fullname TEXT;
    tempschema TEXT;
    BEGIN
    ret_code := 0;
    fullname := (LOWER(objschema) || '.' || LOWER(objname));
        IF (UPPER(objtype)) = 'TABLE' THEN
        BEGIN
            IF EXISTS( SELECT * FROM pg_tables WHERE tablename = LOWER(objname) AND schemaname = LOWER(objschema) )
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
            IF EXISTS( SELECT * FROM pg_views WHERE viewname = LOWER(objname) AND schemaname = LOWER(objschema) )
            THEN
            EXECUTE 'DROP VIEW ' || fullname;
            ret_code = 1;
            END IF;
        END;
        ELSEIF (UPPER(objtype)) = 'INDEX' THEN
        BEGIN
            fullname := LOWER(objschema) || '.' || LOWER(subobjname);
            IF EXISTS( SELECT * FROM pg_indexes WHERE tablename = LOWER(objname) AND indexname = LOWER(subobjname) AND schemaname = LOWER(objschema) )
            THEN
            EXECUTE 'DROP INDEX ' || fullname;
            ret_code = 1;
            ELSE
            IF EXISTS( SELECT * FROM pg_indexes WHERE indexname = LOWER(subobjname) AND schemaname = LOWER(objschema) )
                THEN RAISE EXCEPTION 'INDEX - % defined on a different table.', subobjname;
            END IF;
            END IF;
        END;
        ELSEIF (UPPER(objtype)) = 'CONSTRAINT' THEN
        BEGIN
            IF EXISTS( SELECT * FROM pg_class LEFT JOIN pg_constraint ON conrelid = pg_class.oid INNER JOIN pg_namespace ON pg_namespace.oid = pg_class.relnamespace
                WHERE relkind = 'r' AND contype IS NOT NULL AND nspname = LOWER(objschema) AND relname = LOWER(objname) AND conname = LOWER(subobjname) )
            THEN
                EXECUTE 'ALTER TABLE ' || fullname || ' DROP CONSTRAINT ' || subobjname;
                ret_code = 1;
            END IF;
        END;
        ELSEIF (UPPER(objtype)) = 'DEFAULT' THEN
        BEGIN
            EXECUTE 'ALTER TABLE ' || fullname || ' ALTER COLUMN ' || subobjname || ' DROP DEFAULT';
            ret_code = 1;
        END;
        ELSEIF (UPPER(objtype)) = 'SCHEMA' THEN
        BEGIN
            IF EXISTS( SELECT * FROM pg_namespace WHERE nspname = LOWER(objschema))
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

ALTER TABLE core.Containers
  ADD Title VARCHAR(1000);

ALTER TABLE core.Documents ADD LastIndexed TIMESTAMP NULL;

-- Add support for password expiration and password history
ALTER TABLE core.Logins
    ADD LastChanged TIMESTAMP NULL,
    ADD PreviousCrypts VARCHAR(1000);

-- Set all password last changed dates to account creation date
UPDATE core.Logins SET LastChanged = (SELECT Created FROM core.UsersData ud JOIN core.Principals p ON ud.UserId = p.UserId WHERE Email = p.Name);

-- Put current password crypt into history
UPDATE core.Logins SET PreviousCrypts = Crypt;
