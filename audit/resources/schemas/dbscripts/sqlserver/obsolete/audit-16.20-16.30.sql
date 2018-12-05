/*
 * Copyright (c) 2016 LabKey Corporation
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

CREATE PROCEDURE audit.updateSelectQueryIdentifiedData AS
BEGIN
DECLARE
    @tempName VARCHAR(100);

    SELECT @tempName = storagetablename
        FROM exp.domaindescriptor WHERE name = 'SelectQueryAuditDomain';
    IF (@tempName IS NOT NULL)
    BEGIN
        EXEC ('ALTER TABLE audit.' + @tempName + ' ALTER COLUMN identifieddata NVARCHAR(MAX)')
    END
    RETURN 0;
END
GO

EXEC audit.updateSelectQueryIdentifiedData;

DROP PROCEDURE audit.updateSelectQueryIdentifiedData;