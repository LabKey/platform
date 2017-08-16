EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'IX_PV_SequenceNum';
EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'ix_participantvisit_sequencenum';
EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'ix_participantvisit_visitrowid';

-- For Resync perf
CREATE INDEX ix_participantvisit_sequencenum ON study.participantvisit (container, participantid, sequencenum, ParticipantSequenceNum);

-- Adding as an explicit index because it got lost on postgresql as an include column
CREATE INDEX ix_participantvisit_visitrowid ON study.participantvisit (visitrowid);