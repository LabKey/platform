/* study-16.10-16.11.sql */

SELECT core.executeJavaUpgradeCode('upgradeLocationTables');

/* study-16.11-16.12.sql */

SELECT core.fn_dropifexists('report', 'study', 'TABLE', NULL);

/* study-16.12-16.13.sql */

SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_PV_SequenceNum');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_ParticipantVisit_ParticipantId');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_ParticipantVisit_SequenceNum');

CREATE INDEX IX_PV_SequenceNum ON study.ParticipantVisit (Container, SequenceNum);