--Drop existing indexes, if they exist
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_PV_SequenceNum');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'ix_participantvisit_sequencenum');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'ix_participantvisit_visitrowid');

--For Resync perf
CREATE INDEX ix_participantvisit_sequencenum ON study.participantvisit (container, participantid, sequencenum, ParticipantSequenceNum);
CREATE INDEX ix_participantvisit_visitrowid ON study.participantvisit (visitrowid);