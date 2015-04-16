/*
 * Copyright (c) 2010-2015 LabKey Corporation
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

/* search-0.00-10.10.sql */

CREATE SCHEMA search;
GO

CREATE TABLE search.CrawlCollections
(
    id INT IDENTITY(1,1),

    Parent INT,
    Name NVARCHAR(448) NOT NULL,
    Path NVARCHAR(2000) NOT NULL,
    csPath AS CHECKSUM(Path),

    Modified DATETIME NULL,
    LastCrawled DATETIME NULL,
    ChangeInterval INT NULL DEFAULT 1000*60*60*24,
    NextCrawl DATETIME NOT NULL DEFAULT CAST('1967-10-04' as DATETIME),

    -- NOTE: Path is too long to use for primary key
    CONSTRAINT PK_Collections PRIMARY KEY (id),
    CONSTRAINT AK_Unique UNIQUE (Parent, Name)
);
CREATE INDEX IDX_PathHash ON search.CrawlCollections(csPath);
CREATE INDEX IDX_NextCrawl ON search.CrawlCollections(NextCrawl);

CREATE TABLE search.CrawlResources
(
    Parent INT,
    Name NVARCHAR(448) NOT NULL,

    Modified DATETIME NULL,    -- filesystem time
    LastIndexed DATETIME NULL,  -- server time
    CONSTRAINT PK_Resources PRIMARY KEY (Parent,Name)
);

CREATE TABLE search.ParticipantIndex
(
    Container ENTITYID NOT NULL,          -- see core.containers
    ParticipantId NVARCHAR(32) NOT NULL,   -- see study.participantvisit
    LastIndexed DATETIME NOT NULL,
    CONSTRAINT PK_ParticipantIndex PRIMARY KEY (Container,ParticipantId)
);

/* search-12.30-13.10.sql */

-- We now use the search index (not a side table) to boost participant results.
DROP TABLE search.ParticipantIndex;