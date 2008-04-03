-- clean up user history prefs for deleted users (issue#5465)
DELETE FROM core.UserHistory WHERE UserID NOT IN
    (
    SELECT U1.UserId
    FROM core.Users U1
  	)
Go
