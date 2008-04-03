ALTER TABLE study.Study ADD StudySecurity BIT DEFAULT 0
Go

UPDATE study.Study SET StudySecurity=1 where StudySecurity is NULL
Go

