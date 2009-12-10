
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
CLUSTER CrawlResources USING PK_Resources;
