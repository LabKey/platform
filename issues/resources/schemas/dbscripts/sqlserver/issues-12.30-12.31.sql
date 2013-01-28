-- Move the column settings from properties to a proper table, add column permissions
CREATE TABLE issues.CustomColumns
(
    Container ENTITYID NOT NULL,
    Name VARCHAR(50) NOT NULL,
    Caption VARCHAR(200) NOT NULL,
    PickList BIT NOT NULL DEFAULT 0,
    Permission VARCHAR(300) NOT NULL,

    CONSTRAINT PK_CustomColumns PRIMARY KEY (Container, Name)
);

INSERT INTO issues.CustomColumns
    SELECT ObjectId AS Container, LOWER(Name), Value AS Caption,
        CASE WHEN CHARINDEX
        (
            Name, (SELECT Value FROM prop.PropertyEntries pl WHERE Category = 'IssuesCaptions'
             AND Name = 'pickListColumns' AND pe.ObjectId = pl.ObjectId)
        ) > 0 THEN 1 ELSE 0 END AS PickList, 'org.labkey.api.security.permissions.ReadPermission' AS Permission
    FROM prop.PropertyEntries pe WHERE Category = 'IssuesCaptions' AND Name <> 'pickListColumns';
