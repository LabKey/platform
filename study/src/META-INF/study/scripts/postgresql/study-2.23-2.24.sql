ALTER TABLE study.Study ADD StudySecurity Boolean DEFAULT false;

UPDATE study.Study SET StudySecurity=true;
