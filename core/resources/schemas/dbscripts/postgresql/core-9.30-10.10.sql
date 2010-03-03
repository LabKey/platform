/* core-9.30-9.31.sql */



/* core-9.31-9.32.sql */

/* PostgreSQL Version */

alter table core.Containers
  add ExperimentID int,
  add Description varchar(4000);

/* core-9.32-9.33.sql */

-- experiment id should not be on Containers table
alter table core.Containers
drop column ExperimentID;

/* core-9.33-9.34.sql */

alter table core.Containers
  add Workbook boolean not null default false;

/* core-9.34-9.35.sql */

-- Add ability to drop default
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

/* core-9.35-9.36.sql */

alter table core.Containers
  add Title varchar(1000);

/* core-9.36-9.37.sql */

alter table core.Documents add LastIndexed timestamp null;

/* core-9.37-9.38.sql */

-- Add support for password expiration and password history
ALTER TABLE core.Logins
    ADD LastChanged TIMESTAMP NULL,
    ADD PreviousCrypts VARCHAR(1000);

-- Set all password last changed dates to account creation date
UPDATE core.Logins SET LastChanged = (SELECT Created FROM core.UsersData ud JOIN core.Principals p ON ud.UserId = p.UserId WHERE Email = p.Name);

-- Put current password crypt into history
UPDATE core.Logins SET PreviousCrypts = Crypt;