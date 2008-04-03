-- clean up orphaned email prefs for deleted users (issue#5333)
DELETE FROM issues.EmailPrefs WHERE UserID NOT IN
    (
    SELECT U1.UserId
    FROM core.Users U1
  	);
