/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
/* issues-12.30-12.31.sql */

-- Move the column settings from properties to a proper table, add column permissions
CREATE TABLE issues.CustomColumns
(
    Container ENTITYID NOT NULL,
    Name VARCHAR(50) NOT NULL,
    Caption VARCHAR(200) NOT NULL,
    PickList BOOLEAN NOT NULL DEFAULT '0',
    Permission VARCHAR(300) NOT NULL,

    CONSTRAINT PK_CustomColumns PRIMARY KEY (Container, Name)
);

INSERT INTO issues.CustomColumns
    SELECT ObjectId AS Container, LOWER(Name), Value AS Caption,
        STRPOS
        (
            (SELECT Value FROM prop.PropertyEntries pl WHERE Category = 'IssuesCaptions'
             AND Name = 'pickListColumns' AND pe.ObjectId = pl.ObjectId), Name
        ) > 0 AS PickList, 'org.labkey.api.security.permissions.ReadPermission' AS Permission
    FROM prop.PropertyEntries pe WHERE Category = 'IssuesCaptions' AND Name <> 'pickListColumns';

/* issues-12.31-12.32.sql */

-- These properties have been moved to a dedicated table
DELETE FROM prop.Properties WHERE Set IN (SELECT Set FROM prop.PropertySets WHERE Category = 'IssuesCaptions');
DELETE FROM prop.PropertySets WHERE Category = 'IssuesCaptions';

/* issues-12.32-12.33.sql */

UPDATE issues.issues SET type = NULL WHERE type = '';
UPDATE issues.issues SET area = NULL WHERE area = '';
UPDATE issues.issues SET milestone = NULL WHERE milestone = '';
UPDATE issues.issues SET resolution = NULL WHERE resolution = '';
UPDATE issues.issues SET string1 = NULL WHERE string1 = '';
UPDATE issues.issues SET string2 = NULL WHERE string2 = '';
UPDATE issues.issues SET string3 = NULL WHERE string3 = '';
UPDATE issues.issues SET string4 = NULL WHERE string4 = '';
UPDATE issues.issues SET string5 = NULL WHERE string5 = '';