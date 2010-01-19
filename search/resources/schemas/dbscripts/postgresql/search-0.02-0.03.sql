/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
SET search_path TO search, public;  -- include public to get ENTITYID, USERID


CREATE TABLE CrawlCollections
(
  id SERIAL,

  Parent INT,
  Name VARCHAR(448) NOT NULL,
  Path VARCHAR(2000) NOT NULL,

  Modified TIMESTAMP NULL,      -- newest file at last crawl
  LastCrawled TIMESTAMP NULL,   -- last crawl (or attemted crawl)
  ChangeInterval int NULL DEFAULT 1000*60*60*24,  -- daily
  NextCrawl TIMESTAMP NOT NULL DEFAULT CAST('1967-10-04' as TIMESTAMP), -- approx LastCrawled + 1/2 * ChangeInterval
  CONSTRAINT PK_Collections PRIMARY KEY (id),
  CONSTRAINT AK_Unique UNIQUE (Parent, Name)
);
CREATE INDEX IDX_NextCrawl ON CrawlCollections(NextCrawl);


CREATE TABLE CrawlResources
(
  Parent INT,
  Name VARCHAR(400) NOT NULL,
  -- file system time and labkey server time may differ
  Modified TIMESTAMP NULL,    -- filesystem time
  LastIndexed TIMESTAMP NULL,  -- server time
  CONSTRAINT PK_Resources PRIMARY KEY (Parent,Name)
);
CLUSTER PK_Resources ON CrawlResources;
