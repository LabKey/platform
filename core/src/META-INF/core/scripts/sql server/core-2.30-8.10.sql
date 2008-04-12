/* core-2.30-2.31.sql */

ALTER TABLE core.Report ADD Flags INT NOT NULL DEFAULT 0
GO

/* core-2.31-2.32.sql */

-- clean up user history prefs for deleted users (issue#5465)
DELETE FROM core.UserHistory WHERE UserID NOT IN
    (
    SELECT U1.UserId
    FROM core.Users U1
    )
GO