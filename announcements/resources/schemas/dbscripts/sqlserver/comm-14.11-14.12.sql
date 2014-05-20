CREATE TABLE comm.RSSFeeds
(
    RowId INT IDENTITY(1,1) NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Container ENTITYID NOT NULL,
    FeedName NVARCHAR(250) NULL,
    FeedURL NVARCHAR(1000) NOT NULL,
    LastRead DATETIME NULL,
    Content NVARCHAR(MAX),

    CONSTRAINT PK_RSSFeeds PRIMARY KEY (RowId),
    CONSTRAINT UQ_RSSFeeds UNIQUE CLUSTERED (Container, RowId)
);