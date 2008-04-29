-- Used to add attachments to issues
ALTER TABLE issues.Comments
    ADD COLUMN EntityId ENTITYID;
