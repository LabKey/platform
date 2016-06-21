EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'IX_PV_SequenceNum';
EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'IX_ParticipantVisit_ParticipantId';
EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'IX_ParticipantVisit_SequenceNum';

CREATE INDEX IX_PV_SequenceNum ON study.ParticipantVisit (Container, SequenceNum) INCLUDE (VisitRowId);
