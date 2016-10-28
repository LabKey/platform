/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
/* comm-14.11-14.12.sql */

CREATE TABLE comm.RSSFeeds
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    Container ENTITYID NOT NULL,
    FeedName VARCHAR(250) NULL,
    FeedURL VARCHAR(1000) NOT NULL,
    LastRead TIMESTAMP NULL,
    Content TEXT,

    CONSTRAINT PK_RSSFeeds PRIMARY KEY (RowId),
    CONSTRAINT UQ_RSSFeeds UNIQUE (Container, RowId)
);

/* comm-14.12-14.13.sql */

ALTER TABLE comm.announcements DROP COLUMN EmailList;