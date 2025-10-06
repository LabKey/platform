DELETE FROM comm.UserList WHERE MessageId NOT IN (SELECT RowId FROM comm.Announcements);
ALTER TABLE comm.UserList ADD CONSTRAINT FK_UserList_Announcements FOREIGN KEY (MessageId) REFERENCES comm.Announcements (RowId);
