/*
 * Copyright (c) 2013 LabKey Corporation
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
-- Move the column settings from properties to a proper table, add column permissions
CREATE TABLE issues.CustomColumns
(
    Container ENTITYID NOT NULL,
    Name VARCHAR(50) NOT NULL,
    Caption VARCHAR(200) NOT NULL,
    PickList BIT NOT NULL DEFAULT 0,
    Permission VARCHAR(300) NOT NULL,

    CONSTRAINT PK_CustomColumns PRIMARY KEY (Container, Name)
);

INSERT INTO issues.CustomColumns
    SELECT ObjectId AS Container, LOWER(Name), Value AS Caption,
        CASE WHEN CHARINDEX
        (
            Name, (SELECT Value FROM prop.PropertyEntries pl WHERE Category = 'IssuesCaptions'
             AND Name = 'pickListColumns' AND pe.ObjectId = pl.ObjectId)
        ) > 0 THEN 1 ELSE 0 END AS PickList, 'org.labkey.api.security.permissions.ReadPermission' AS Permission
    FROM prop.PropertyEntries pe WHERE Category = 'IssuesCaptions' AND Name <> 'pickListColumns';
