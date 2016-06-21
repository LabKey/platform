SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_PV_SequenceNum');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_ParticipantVisit_ParticipantId');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_ParticipantVisit_SequenceNum');

CREATE INDEX IX_PV_SequenceNum ON study.ParticipantVisit (Container, SequenceNum);
