/* comm-18.10-18.11.sql */

ALTER TABLE comm.Announcements ADD COLUMN Approved TIMESTAMP NULL;

UPDATE comm.Announcements SET Approved = Created;