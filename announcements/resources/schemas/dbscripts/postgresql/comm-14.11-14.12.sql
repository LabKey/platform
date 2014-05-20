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