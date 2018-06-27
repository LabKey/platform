ALTER TABLE comm.Announcements ADD Approved DATETIME NULL;
GO

UPDATE comm.Announcements SET Approved = Created;
