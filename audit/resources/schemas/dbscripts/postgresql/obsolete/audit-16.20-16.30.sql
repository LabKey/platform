/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
/* audit-16.20-16.21.sql */

CREATE OR REPLACE FUNCTION audit.updateSelectQueryIdentifiedData() RETURNS integer AS $BODY$

DECLARE
    tempName TEXT;
BEGIN
    SELECT INTO tempName storagetablename
        FROM exp.domaindescriptor WHERE name = 'SelectQueryAuditDomain';
    IF (tempName IS NOT NULL)
    THEN
        EXECUTE 'ALTER TABLE audit.' || tempName || ' ALTER COLUMN identifieddata TYPE TEXT';
    END IF;
    RETURN 0;
END

$BODY$
LANGUAGE plpgsql;

SELECT audit.updateSelectQueryIdentifiedData();

DROP FUNCTION audit.updateSelectQueryIdentifiedData();