-- move the StudyPublishProtocol container to the shared folder
UPDATE exp.protocol
    SET container = (SELECT entityid FROM core.containers WHERE name = 'Shared')
    WHERE lsid LIKE 'urn:lsid:labkey.org:Protocol:StudyPublishProtocol%';