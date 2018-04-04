ALTER TABLE comm.Announcements ADD COLUMN Approved TIMESTAMP NULL;

UPDATE comm.Announcements SET Approved = Created;
