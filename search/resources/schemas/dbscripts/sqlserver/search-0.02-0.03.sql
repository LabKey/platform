
EXEC sp_addapprole 'search', 'password'
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
  ChangeInterval int NULL DEFAULT 1000*60*60*24,
  NextCrawl DATETIME NOT NULL DEFAULT CAST('1967-10-04' as DATETIME),

  -- NOTE: Path is too long to use for primary key
  CONSTRAINT PK_Collections PRIMARY KEY (id),
  CONSTRAINT AK_Unique UNIQUE (Parent, Name)
)
GO
CREATE INDEX IDX_PathHash ON search.CrawlCollections(csPath);
CREATE INDEX IDX_NextCrawl ON search.CrawlCollections(NextCrawl);
GO


CREATE TABLE search.CrawlResources
(
  Parent INT,
  Name NVARCHAR(448) NOT NULL,

  Modified DATETIME NULL,    -- filesystem time
  LastIndexed DATETIME NULL,  -- server time
  CONSTRAINT PK_Resources PRIMARY KEY (Parent,Name)
);
GO
