-- Remove the ability to drop AGGREGATE, DEFAULT, FUNCTION, PROCEDURE, and SCHEMA. These are not used in current
-- scripts and, if needed in the future, conditional drops are easy to affect in standard PostgreSQL DDL now. This
-- stored function is deprecated and all calls have been removed on develop. However, we're leaving the function in
-- place (supporting only five object types) for now to ease merges forward from older branches that might use it.
CREATE OR REPLACE FUNCTION core.fn_dropifexists(
    text,
    text,
    text,
    text)
    RETURNS integer AS
$BODY$
/*
  Function to safely drop database object types without error if the object does not exist.

  Column deletion will cascade to any dependent objects.

     Usage:
     SELECT core.fn_dropifexists(objname, objschema, objtype, subobjname), where:

     objname    Required. For TABLE or VIEW, the name of the object to be dropped.
                 For INDEX, CONSTRAINT, or COLUMN, the name of the associated table.
     objschema  Required. The name of the object's schema
     objtype    Required. The type of object being dropped. Valid values are TABLE, VIEW, INDEX, CONSTRAINT, COLUMN.
     subobjtype Required. When dropping INDEX, CONSTRAINT, or COLUMN, the name of the object being dropped. Otherwise, NULL.
 */
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
    ELSEIF (UPPER(objtype)) = 'COLUMN' THEN
        BEGIN
            IF EXISTS( SELECT * FROM information_schema.columns WHERE table_name = LOWER(objname) AND table_schema = LOWER(objschema) AND column_name = LOWER(subobjname))
            THEN
                EXECUTE 'ALTER TABLE ' || fullname || ' DROP COLUMN ' || subobjname || ' CASCADE';
                ret_code = 1;
            END IF;
        END;
    ELSE
        RAISE EXCEPTION 'Invalid object type - %;  Valid values are TABLE, VIEW, INDEX, CONSTRAINT, COLUMN ', objtype;
    END IF;

    RETURN ret_code;
END;
$BODY$
LANGUAGE plpgsql VOLATILE
COST 100;
