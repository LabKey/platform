/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

-- represents a grouping category for views (reports etc.)
CREATE TABLE core.ViewCategory
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP DEFAULT now(),
    ModifiedBy USERID,
    Modified TIMESTAMP DEFAULT now(),

    Label VARCHAR(200) NOT NULL,
    DisplayOrder Integer NOT NULL DEFAULT 0,

    CONSTRAINT pk_viewCategory PRIMARY KEY (RowId),
    CONSTRAINT uq_container_label UNIQUE (Container, Label)
);

ALTER TABLE core.Report ADD COLUMN CategoryId Integer;
ALTER TABLE core.Report ADD COLUMN DisplayOrder Integer NOT NULL DEFAULT 0;
