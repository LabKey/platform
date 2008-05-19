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


CREATE OR REPLACE FUNCTION core.fn_dropifexists (text, text, text, text) RETURNS integer AS '
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
    fullname := (lower(objschema)||''.''||lower(objname));
	    IF (upper(objtype)) = ''TABLE'' THEN
		BEGIN
            IF EXISTS( SELECT * FROM pg_tables WHERE tablename = lower(objname) and schemaname = lower(objschema) )
            THEN
                EXECUTE ''DROP TABLE ''||fullname;
                ret_code = 1;
            ELSE
                BEGIN
                    SELECT INTO tempschema schemaname FROM pg_tables WHERE tablename = lower(objname) and schemaname LIKE ''%temp%'';
                    IF (tempschema IS NOT NULL)
                    THEN
                        EXECUTE ''DROP TABLE ''|| tempschema || ''.'' || objname;
                        ret_code = 1;
                    END IF;
                END;
            END IF;
		END;
	    ELSEIF (upper(objtype)) = ''VIEW'' THEN
		BEGIN
		    IF EXISTS( SELECT * FROM pg_views WHERE viewname = lower(objname) and schemaname = lower(objschema) )
		    THEN
			EXECUTE ''DROP VIEW ''||fullname;
			ret_code = 1;
		    END IF;
		END;
	    ELSEIF (upper(objtype)) = ''INDEX'' THEN
		BEGIN
		    fullname := lower(objschema) || ''.'' || lower(subobjname);
		    IF EXISTS( SELECT * FROM pg_indexes WHERE tablename = lower(objname) and indexname = lower(subobjname) and schemaname = lower(objschema) )
		    THEN
			EXECUTE ''DROP INDEX ''|| fullname;
			ret_code = 1;
		    ELSE
			IF EXISTS( SELECT * FROM pg_indexes WHERE indexname = lower(subobjname) and schemaname = lower(objschema) )
				THEN RAISE EXCEPTION ''INDEX - % defined on a different table.'', subobjname;
			END IF;
		    END IF;
		END;
	    ELSEIF (upper(objtype)) = ''SCHEMA'' THEN
		BEGIN
		    IF EXISTS( SELECT * FROM pg_namespace WHERE nspname = lower(objschema))
		    THEN
			IF objname = ''*'' THEN
				EXECUTE ''DROP SCHEMA ''|| lower(objschema) || '' CASCADE'';
				ret_code = 1;
			ELSEIF (objname = '''' OR objname IS NULL) THEN
				EXECUTE ''DROP SCHEMA ''|| lower(objschema) || '' RESTRICT'';
				ret_code = 1;
			ELSE
				RAISE EXCEPTION ''Invalid objname for objtype of SCHEMA;  must be either "*" (for DROP SCHEMA CASCADE)  or NULL (for DROP SCHEMA RESTRICT)'';
			END IF;
		    END IF;
		END;
	    ELSE
		RAISE EXCEPTION ''Invalid object type - %;  Valid values are TABLE, VIEW, INDEX, SCHEMA '', objtype;
	    END IF;

	RETURN ret_code;
	END;
' LANGUAGE plpgsql;


SELECT core.fn_dropifexists ('Containers', 'core', 'Index', 'IX_Containers_Parent_Entity');
SELECT core.fn_dropifexists ('Documents', 'core', 'Index', 'IX_Documents_Container');
SELECT core.fn_dropifexists ('Documents', 'core', 'Index', 'IX_Documents_Parent');

CREATE INDEX IX_Containers_Parent_Entity ON core.Containers(Parent, EntityId);
ALTER TABLE core.Containers ADD CONSTRAINT UQ_Containers_RowId UNIQUE (RowId);
CREATE INDEX IX_Documents_Container ON core.Documents(Container);
CREATE INDEX IX_Documents_Parent ON core.Documents(Parent);

ALTER TABLE core.Containers ADD
    SortOrder INTEGER NOT NULL DEFAULT 0;

ALTER TABLE core.Report
    ADD COLUMN ReportOwner INT;
