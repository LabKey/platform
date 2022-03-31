CREATE TABLE comm.PageAliases
(
    Container ENTITYID NOT NULL,
    Alias VARCHAR(255) NOT NULL,
    RowId INT NOT NULL,

    CONSTRAINT PK_PageAliases PRIMARY KEY (Container, Alias)
);
