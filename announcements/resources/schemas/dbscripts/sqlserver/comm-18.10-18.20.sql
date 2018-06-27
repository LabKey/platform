/* comm-18.10-18.11.sql */

ALTER TABLE comm.Announcements ADD Approved DATETIME NULL;
GO

UPDATE comm.Announcements SET Approved = Created;