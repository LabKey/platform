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

CREATE SCHEMA search;

CREATE TABLE search.CrawlCollections
(
    id SERIAL,

    Parent INT,
    Name VARCHAR(448) NOT NULL,
    Path VARCHAR(2000) NOT NULL,

    Modified TIMESTAMP NULL,      -- newest file at last crawl
    LastCrawled TIMESTAMP NULL,   -- last crawl (or attempted crawl)
    ChangeInterval INT NULL DEFAULT 1000*60*60*24,  -- daily
    NextCrawl TIMESTAMP NOT NULL DEFAULT CAST('1967-10-04' as TIMESTAMP), -- approx LastCrawled + 1/2 * ChangeInterval
    CONSTRAINT PK_Collections PRIMARY KEY (id),
    CONSTRAINT AK_Unique UNIQUE (Parent, Name)
);
CREATE INDEX IDX_NextCrawl ON search.CrawlCollections(NextCrawl);

CREATE TABLE search.CrawlResources
(
    Parent INT,
    Name VARCHAR(400) NOT NULL,
    -- file system time and labkey server time may differ
    Modified TIMESTAMP NULL,    -- filesystem time
    LastIndexed TIMESTAMP NULL,  -- server time
    CONSTRAINT PK_Resources PRIMARY KEY (Parent,Name)
);
CLUSTER PK_Resources ON search.CrawlResources;

/* search-0.03-0.04.sql */

CREATE TABLE search.ParticipantIndex
(
    Container ENTITYID NOT NULL,          -- see core.containers
    ParticipantId VARCHAR(32) NOT NULL,   -- see study.participantvisit
    LastIndexed TIMESTAMP NOT NULL,
    CONSTRAINT PK_ParticipantIndex PRIMARY KEY (Container,ParticipantId)
);