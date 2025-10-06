DELETE FROM core.Members WHERE GroupId NOT IN (SELECT UserId FROM core.Principals WHERE Type IN ('g', 'm'));
ALTER TABLE core.Members ADD CONSTRAINT FK_Members_Principals FOREIGN KEY (GroupId) REFERENCES core.Principals (UserId);
CREATE INDEX IX_Members_GroupId ON core.Members(GroupId);
