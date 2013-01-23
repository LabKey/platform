/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// rows data is Days, CD4, and Hemoglobin for PTIDS 249318596, 249320107, 249320127, 249320489, 249320897, 249325717
var labResultsData = {"schemaName":"study","queryName":"study-temp-3","formatVersion":9.1,"metaData":{"totalProperty":"rowCount","root":"rows","fields":[{"isVersionField":false,"measure":false,"facetingBehaviorType":"AUTOMATIC","shownInDetailsView":true,"fieldKeyArray":["study_LabResults_ParticipantId"],"type":"string","fieldKey":"study_LabResults_ParticipantId","ext":{},"userEditable":true,"keyField":false,"jsonType":"string","mvEnabled":false,"sqlType":"varchar","description":"Subject identifier","inputType":"text","name":"study_LabResults_ParticipantId","isReadOnly":false,"isNullable":false,"fieldKeyPath":"study_LabResults_ParticipantId","shortCaption":"Study Lab Results Participant Id","shownInInsertView":true,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Lab Results Participant Id","nullable":false,"isAutoIncrement":false,"isHidden":false,"dimension":true,"isSelectable":true,"readOnly":false,"friendlyType":"Text (String)","importAliases":["ptid"],"selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":false,"facetingBehaviorType":"AUTOMATIC","shownInDetailsView":true,"fieldKeyArray":["study_LabResults_ParticipantVisitVisitDate"],"type":"date","fieldKey":"study_LabResults_ParticipantVisitVisitDate","ext":{},"userEditable":true,"keyField":false,"jsonType":"date","mvEnabled":false,"sqlType":"timestamp","description":"The date of the visit.  Primarily used in date-based studies.","inputType":"text","name":"study_LabResults_ParticipantVisitVisitDate","isReadOnly":false,"isNullable":true,"fieldKeyPath":"study_LabResults_ParticipantVisitVisitDate","shortCaption":"Visit Date","shownInInsertView":true,"isMvEnabled":false,"extFormatFn":"(Ext.util.Format.dateRenderer('Y-m-d'))","autoIncrement":false,"caption":"Study Lab Results Participant Visit Visit Date","nullable":true,"format":"yyyy-MM-dd","extFormat":"Y-m-d","isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Date and Time","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":true,"facetingBehaviorType":"AUTOMATIC","shownInDetailsView":true,"fieldKeyArray":["study_LabResults_CD4"],"type":"int","fieldKey":"study_LabResults_CD4","ext":{},"userEditable":true,"keyField":false,"jsonType":"int","mvEnabled":false,"sqlType":"int4","inputType":"text","name":"study_LabResults_CD4","isReadOnly":false,"isNullable":true,"fieldKeyPath":"study_LabResults_CD4","shortCaption":"Study Lab Results CD4","shownInInsertView":true,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Lab Results CD4","nullable":true,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Integer","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":true,"facetingBehaviorType":"AUTOMATIC","shownInDetailsView":true,"fieldKeyArray":["study_LabResults_Hemoglobin"],"type":"float","fieldKey":"study_LabResults_Hemoglobin","ext":{},"userEditable":true,"keyField":false,"jsonType":"float","mvEnabled":false,"sqlType":"float8","inputType":"text","name":"study_LabResults_Hemoglobin","isReadOnly":false,"isNullable":true,"fieldKeyPath":"study_LabResults_Hemoglobin","shortCaption":"Study Lab Results Hemoglobin","shownInInsertView":true,"isMvEnabled":false,"extFormatFn":"(Ext.util.Format.numberRenderer('00.0'))","autoIncrement":false,"caption":"Study Lab Results Hemoglobin","nullable":true,"format":"#0.0","extFormat":"00.0","isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Number (Double)","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":false,"facetingBehaviorType":"AUTOMATIC","shownInDetailsView":true,"fieldKeyArray":["study_Demographics_StartDate"],"type":"date","fieldKey":"study_Demographics_StartDate","ext":{},"userEditable":true,"keyField":false,"jsonType":"date","mvEnabled":false,"sqlType":"timestamp","inputType":"text","name":"study_Demographics_StartDate","isReadOnly":false,"isNullable":true,"fieldKeyPath":"study_Demographics_StartDate","shortCaption":"Study Demographics Start Date","shownInInsertView":true,"isMvEnabled":false,"extFormatFn":"(Ext.util.Format.dateRenderer('Y-m-d'))","autoIncrement":false,"caption":"Study Demographics Start Date","nullable":true,"format":"yyyy-MM-dd","extFormat":"Y-m-d","isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Date and Time","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":true,"facetingBehaviorType":"AUTOMATIC","shownInDetailsView":true,"fieldKeyArray":["Days"],"type":"int","fieldKey":"Days","ext":{},"userEditable":false,"keyField":false,"jsonType":"int","mvEnabled":false,"sqlType":"INTEGER","inputType":"text","name":"Days","isReadOnly":false,"isNullable":true,"fieldKeyPath":"Days","shortCaption":"Days","shownInInsertView":false,"isMvEnabled":false,"autoIncrement":false,"caption":"Days","nullable":true,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Integer","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":false,"isUserEditable":false}]},"columnModel":[{"scale":32,"hidden":false,"sortable":true,"align":"left","width":10,"dataIndex":"study_LabResults_ParticipantId","required":true,"editable":false,"header":"Study Lab Results Participant Id","tooltip":"Subject identifier"},{"scale":29,"hidden":false,"sortable":true,"align":"left","width":90,"dataIndex":"study_LabResults_ParticipantVisitVisitDate","required":false,"editable":false,"header":"Study Lab Results Participant Visit Visit Date","tooltip":"The date of the visit.  Primarily used in date-based studies."},{"scale":10,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"study_LabResults_CD4","required":false,"editable":false,"header":"Study Lab Results CD4"},{"scale":20,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"study_LabResults_Hemoglobin","required":false,"editable":false,"header":"Study Lab Results Hemoglobin"},{"scale":100,"hidden":false,"sortable":true,"align":"left","width":90,"dataIndex":"study_Demographics_StartDate","required":false,"editable":false,"header":"Study Demographics Start Date"},{"scale":0,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"Days","required":false,"editable":false,"header":"Days"}],"measureToColumn":{"Hemoglobin":"study_LabResults_Hemoglobin","StartDate":"study_Demographics_StartDate","ParticipantId":"study_LabResults_ParticipantId","CD4":"study_LabResults_CD4","ParticipantVisit/VisitDate":"study_LabResults_ParticipantVisitVisitDate"},"rows":[{"Days":{"value":0},"study_Demographics_StartDate":{"value":"2008/05/17 00:00:00"},"study_LabResults_ParticipantId":{"value":"249318596","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249318596"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/05/17 00:00:00"},"study_LabResults_CD4":{"value":543},"study_LabResults_Hemoglobin":{"value":14.5}},{"Days":{"value":79},"study_Demographics_StartDate":{"value":"2008/05/17 00:00:00"},"study_LabResults_ParticipantId":{"value":"249318596","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249318596"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/08/04 00:00:00"},"study_LabResults_CD4":{"value":520},"study_LabResults_Hemoglobin":{"value":16}},{"Days":{"value":108},"study_Demographics_StartDate":{"value":"2008/05/17 00:00:00"},"study_LabResults_ParticipantId":{"value":"249318596","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249318596"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/09/02 00:00:00"},"study_LabResults_CD4":{"value":420},"study_LabResults_Hemoglobin":{"value":12.2}},{"Days":{"value":190},"study_Demographics_StartDate":{"value":"2008/05/17 00:00:00"},"study_LabResults_ParticipantId":{"value":"249318596","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249318596"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/11/23 00:00:00"},"study_LabResults_CD4":{"value":185},"study_LabResults_Hemoglobin":{"value":15.5}},{"Days":{"value":246},"study_Demographics_StartDate":{"value":"2008/05/17 00:00:00"},"study_LabResults_ParticipantId":{"value":"249318596","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249318596"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/01/18 00:00:00"},"study_LabResults_CD4":{"value":261},"study_LabResults_Hemoglobin":{"value":13.9}},{"Days":{"value":276},"study_Demographics_StartDate":{"value":"2008/05/17 00:00:00"},"study_LabResults_ParticipantId":{"value":"249318596","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249318596"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/02/17 00:00:00"},"study_LabResults_CD4":{"value":308},"study_LabResults_Hemoglobin":{"value":13.7}},{"Days":{"value":303},"study_Demographics_StartDate":{"value":"2008/05/17 00:00:00"},"study_LabResults_ParticipantId":{"value":"249318596","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249318596"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/03/16 00:00:00"},"study_LabResults_CD4":{"value":177},"study_LabResults_Hemoglobin":{"value":12.9}},{"Days":{"value":335},"study_Demographics_StartDate":{"value":"2008/05/17 00:00:00"},"study_LabResults_ParticipantId":{"value":"249318596","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249318596"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/04/17 00:00:00"},"study_LabResults_CD4":{"value":144},"study_LabResults_Hemoglobin":{"value":11.1}},{"Days":{"value":364},"study_Demographics_StartDate":{"value":"2008/05/17 00:00:00"},"study_LabResults_ParticipantId":{"value":"249318596","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249318596"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/05/16 00:00:00"},"study_LabResults_CD4":{"value":167},"study_LabResults_Hemoglobin":{"value":13.2}},{"Days":{"value":394},"study_Demographics_StartDate":{"value":"2008/05/17 00:00:00"},"study_LabResults_ParticipantId":{"value":"249318596","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249318596"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/06/15 00:00:00"},"study_LabResults_CD4":{"value":154},"study_LabResults_Hemoglobin":{"value":16.1}},{"Days":{"value":0},"study_Demographics_StartDate":{"value":"2008/06/04 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320107","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320107"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/06/04 00:00:00"},"study_LabResults_CD4":{"value":645},"study_LabResults_Hemoglobin":{"value":11}},{"Days":{"value":42},"study_Demographics_StartDate":{"value":"2008/06/04 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320107","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320107"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/07/16 00:00:00"},"study_LabResults_CD4":{"value":234},"study_LabResults_Hemoglobin":{"value":14.3}},{"Days":{"value":56},"study_Demographics_StartDate":{"value":"2008/06/04 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320107","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320107"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_CD4":{"value":344},"study_LabResults_Hemoglobin":{"value":13.2}},{"Days":{"value":105},"study_Demographics_StartDate":{"value":"2008/06/04 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320107","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320107"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/09/17 00:00:00"},"study_LabResults_CD4":{"value":342},"study_LabResults_Hemoglobin":{"value":12.9}},{"Days":{"value":216},"study_Demographics_StartDate":{"value":"2008/06/04 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320107","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320107"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/01/06 00:00:00"},"study_LabResults_CD4":{"value":223},"study_LabResults_Hemoglobin":{"value":16.5}},{"Days":{"value":0},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320127","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320127"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_CD4":{"value":1116},"study_LabResults_Hemoglobin":{"value":13.5}},{"Days":{"value":49},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320127","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320127"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/09/17 00:00:00"},"study_LabResults_CD4":{"value":987},"study_LabResults_Hemoglobin":{"value":19.3}},{"Days":{"value":160},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320127","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320127"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/01/06 00:00:00"},"study_LabResults_CD4":{"value":897},"study_LabResults_Hemoglobin":{"value":21}},{"Days":{"value":193},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320127","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320127"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/02/08 00:00:00"},"study_LabResults_CD4":{"value":1009},"study_LabResults_Hemoglobin":{"value":17.3}},{"Days":{"value":225},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320127","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320127"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/03/12 00:00:00"},"study_LabResults_CD4":{"value":1200},"study_LabResults_Hemoglobin":{"value":15.4}},{"Days":{"value":261},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320127","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320127"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/04/17 00:00:00"},"study_LabResults_CD4":{"value":1300},"study_LabResults_Hemoglobin":{"value":18.3}},{"Days":{"value":283},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320127","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320127"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/05/09 00:00:00"},"study_LabResults_CD4":{"value":1234},"study_LabResults_Hemoglobin":{"value":14.2}},{"Days":{"value":0},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320489","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320489"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_CD4":{"value":324},"study_LabResults_Hemoglobin":{"value":11.9}},{"Days":{"value":58},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320489","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320489"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/09/26 00:00:00"},"study_LabResults_CD4":{"value":435},"study_LabResults_Hemoglobin":{"value":13.3}},{"Days":{"value":126},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320489","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320489"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/12/03 00:00:00"},"study_LabResults_CD4":{"value":234},"study_LabResults_Hemoglobin":{"value":16.4}},{"Days":{"value":169},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320489","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320489"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/01/15 00:00:00"},"study_LabResults_CD4":{"value":175},"study_LabResults_Hemoglobin":{"value":13.3}},{"Days":{"value":190},"study_Demographics_StartDate":{"value":"2008/07/30 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320489","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320489"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/02/05 00:00:00"},"study_LabResults_CD4":{"value":123},"study_LabResults_Hemoglobin":{"value":12.9}},{"Days":{"value":0},"study_Demographics_StartDate":{"value":"2008/05/01 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320897","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320897"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/05/01 00:00:00"},"study_LabResults_CD4":{"value":1045},"study_LabResults_Hemoglobin":{"value":15.6}},{"Days":{"value":43},"study_Demographics_StartDate":{"value":"2008/05/01 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320897","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320897"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/06/13 00:00:00"},"study_LabResults_CD4":{"value":982},"study_LabResults_Hemoglobin":{"value":14.6}},{"Days":{"value":80},"study_Demographics_StartDate":{"value":"2008/05/01 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320897","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320897"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/07/20 00:00:00"},"study_LabResults_CD4":{"value":893},"study_LabResults_Hemoglobin":{"value":12.9}},{"Days":{"value":105},"study_Demographics_StartDate":{"value":"2008/05/01 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320897","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320897"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/08/14 00:00:00"},"study_LabResults_CD4":{"value":783},"study_LabResults_Hemoglobin":{"value":14}},{"Days":{"value":247},"study_Demographics_StartDate":{"value":"2008/05/01 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320897","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320897"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/01/03 00:00:00"},"study_LabResults_CD4":{"value":1200},"study_LabResults_Hemoglobin":{"value":20}},{"Days":{"value":285},"study_Demographics_StartDate":{"value":"2008/05/01 00:00:00"},"study_LabResults_ParticipantId":{"value":"249320897","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249320897"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2009/02/10 00:00:00"},"study_LabResults_CD4":{"value":1100},"study_LabResults_Hemoglobin":{"value":17.2}},{"Days":{"value":0},"study_Demographics_StartDate":{"value":"2008/04/27 00:00:00"},"study_LabResults_ParticipantId":{"value":"249325717","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249325717"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/04/27 00:00:00"},"study_LabResults_CD4":{"value":874},"study_LabResults_Hemoglobin":{"value":18}},{"Days":{"value":24},"study_Demographics_StartDate":{"value":"2008/04/27 00:00:00"},"study_LabResults_ParticipantId":{"value":"249325717","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249325717"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/05/21 00:00:00"},"study_LabResults_CD4":{"value":910},"study_LabResults_Hemoglobin":{"value":12.1}},{"Days":{"value":62},"study_Demographics_StartDate":{"value":"2008/04/27 00:00:00"},"study_LabResults_ParticipantId":{"value":"249325717","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249325717"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/06/28 00:00:00"},"study_LabResults_CD4":{"value":940},"study_LabResults_Hemoglobin":{"value":18.9}},{"Days":{"value":84},"study_Demographics_StartDate":{"value":"2008/04/27 00:00:00"},"study_LabResults_ParticipantId":{"value":"249325717","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249325717"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/07/20 00:00:00"},"study_LabResults_CD4":{"value":897},"study_LabResults_Hemoglobin":{"value":13.2}},{"Days":{"value":210},"study_Demographics_StartDate":{"value":"2008/04/27 00:00:00"},"study_LabResults_ParticipantId":{"value":"249325717","url":"/labkey/study/Imported%20Demo/participant.view?datasetId=5007&participantId=249325717"},"study_LabResults_ParticipantVisitVisitDate":{"value":"2008/11/23 00:00:00"},"study_LabResults_CD4":{"value":950},"study_LabResults_Hemoglobin":{"value":11.9}}],"rowCount":38};
var labResultsRows = labResultsData.rows;

var renderStats = function(){
    var labResultsStats = LABKEY.vis.Stat.summary(labResultsRows, function(row){return row.study_LabResults_CD4.value});
    var statsDiv = document.getElementById('stats');
    var p = document.createElement('p');

    statsDiv.appendChild(document.createElement('p').appendChild(document.createTextNode("Minimum: " + labResultsStats.min + ", Maximum: " + labResultsStats.max)));

    p = document.createElement('p');
    p.appendChild(document.createTextNode("Q1: " + labResultsStats.Q1 + ", Q2 (median): " + labResultsStats.Q2 + ", Q3: " + labResultsStats.Q3 + ", IQR: " + labResultsStats.IQR));
    statsDiv.appendChild(p);

    p = document.createElement('p');
    p.appendChild(document.createTextNode("Sorted Values: " + labResultsStats.sortedValues.join(', ')));
    statsDiv.appendChild(p);
};

var CD4PointLayer = new LABKEY.vis.Layer({
	geom: new LABKEY.vis.Geom.Point({size: 5}),
	name: 'Really Long Name That Gets Wrapped',
	aes: {
		y: function(row){return row.study_LabResults_CD4.value},
		hoverText: function(row){return row.study_LabResults_ParticipantId.value + ' CD4, Day ' + row.Days.value + ", " + row.study_LabResults_CD4.value;}
	}
});

var CD4PathLayer = new LABKEY.vis.Layer({
	geom: new LABKEY.vis.Geom.Path({size: 3, opacity: .2}),
	name: 'Really Long Name That Gets Wrapped',
	aes: {
		y: function(row){return row.study_LabResults_CD4.value}
	}
});

var hemoglobinPointLayer = new LABKEY.vis.Layer({
	geom: new LABKEY.vis.Geom.Point(),
	name: 'Hemoglobin',
	aes: {
		yRight: function(row){return row.study_LabResults_Hemoglobin.value},
		hoverText: function(row){return row.study_LabResults_ParticipantId.value + ' Hemoglobin, day ' + row.Days.value + ', ' + row.study_LabResults_Hemoglobin.value;}
	}
});

var hemoglobinPathLayer = new LABKEY.vis.Layer({
	geom: new LABKEY.vis.Geom.Path({opacity: .2}),
	name: 'Hemoglobin',
	aes: {
		yRight: function(row){return row.study_LabResults_Hemoglobin.value}
	}
});

var plotConfig = {
	renderTo: 'chart',
    labels: {
        x: {value: "Days Since Start Date"},
        y: {value: "CD4+ (cells/mm3)"},
        yRight: {value: "Hemoglobin"},
        main: {value: "Lab Results"}
    },
    width: 900,
	height: 300,
    clipRect: true,
//    legendPos: 'none',
//    bgColor: '#777777',
//    gridColor: '#FF00FF',
//    gridLineColor: "#FFFFFF",
	data: labResultsRows,
	aes: {
		x: function(row){return row.Days.value},
		color: function(row){return row.study_LabResults_ParticipantId.value},
		group: function(row){return row.study_LabResults_ParticipantId.value},
        shape: function(row){return row.study_LabResults_ParticipantId.value}
	},
    scales: {
        x: {
            scaleType: 'continuous',
			trans: 'linear'
        },
        y: {
            scaleType: 'continuous',
			trans: 'linear',
            min: 400,
            max: 1000
        },
        yRight: {
            min: null,
            max: null
        },
        shape: {
            scaleType: 'discrete'
        }
    }
};

var plot = new LABKEY.vis.Plot(plotConfig);

plot.addLayer(CD4PathLayer);
plot.addLayer(CD4PointLayer);
//plot.addLayer(hemoglobinPathLayer);
//plot.addLayer(hemoglobinPointLayer);

var individualData = {"schemaName":"study","queryName":"study-temp-23","formatVersion":9.1,"metaData":{"totalProperty":"rowCount","root":"rows","fields":[{"isVersionField":false,"measure":false,"shownInDetailsView":true,"fieldKeyArray":["study_PhysicalExam_ParticipantId"],"type":"string","fieldKey":"study_PhysicalExam_ParticipantId","ext":{},"userEditable":true,"keyField":false,"jsonType":"string","mvEnabled":false,"sqlType":"varchar","description":"Subject identifier","inputType":"text","name":"study_PhysicalExam_ParticipantId","isReadOnly":false,"isNullable":false,"fieldKeyPath":"study_PhysicalExam_ParticipantId","shortCaption":"Study Physical Exam Participant Id","shownInInsertView":true,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Physical Exam Participant Id","nullable":false,"isAutoIncrement":false,"isHidden":false,"dimension":true,"isSelectable":true,"readOnly":false,"friendlyType":"Text (String)","importAliases":["ptid"],"selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":true,"shownInDetailsView":true,"fieldKeyArray":["study_PhysicalExam_ParticipantVisitsequencenum"],"type":"float","fieldKey":"study_PhysicalExam_ParticipantVisitsequencenum","ext":{},"userEditable":true,"keyField":false,"jsonType":"float","mvEnabled":false,"sqlType":"numeric","description":"The sequence number of the visit.  Primarily used in visit-based studies.","inputType":"text","name":"study_PhysicalExam_ParticipantVisitsequencenum","isReadOnly":false,"isNullable":false,"fieldKeyPath":"study_PhysicalExam_ParticipantVisitsequencenum","shortCaption":"Sequencenum","shownInInsertView":true,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Physical Exam Participant Visitsequencenum","nullable":false,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Number (Double)","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":true,"shownInDetailsView":true,"fieldKeyArray":["study_PhysicalExam_Weight_kg"],"type":"int","fieldKey":"study_PhysicalExam_Weight_kg","ext":{},"userEditable":true,"keyField":false,"jsonType":"int","mvEnabled":false,"sqlType":"int4","inputType":"text","name":"study_PhysicalExam_Weight_kg","isReadOnly":false,"isNullable":true,"fieldKeyPath":"study_PhysicalExam_Weight_kg","shortCaption":"Study Physical Exam Weight Kg","shownInInsertView":true,"isMvEnabled":false,"extFormatFn":"(Ext.util.Format.numberRenderer('000'))","autoIncrement":false,"caption":"Study Physical Exam Weight Kg","nullable":true,"format":"##0","extFormat":"000","isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Integer","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":false,"shownInDetailsView":true,"fieldKeyArray":["study_PhysicalExam_ParticipantVisitVisitLabel"],"type":"string","fieldKey":"study_PhysicalExam_ParticipantVisitVisitLabel","ext":{},"userEditable":true,"keyField":false,"jsonType":"string","mvEnabled":false,"sqlType":"varchar","description":"The long/friendly name of each visit.","inputType":"text","name":"study_PhysicalExam_ParticipantVisitVisitLabel","isReadOnly":false,"isNullable":true,"fieldKeyPath":"study_PhysicalExam_ParticipantVisitVisitLabel","shortCaption":"Visit Label","shownInInsertView":true,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Physical Exam Participant Visit Visit Label","nullable":true,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Text (String)","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":true,"shownInDetailsView":true,"fieldKeyArray":["study_PhysicalExam_ParticipantVisitVisitDisplayOrder"],"type":"int","fieldKey":"study_PhysicalExam_ParticipantVisitVisitDisplayOrder","ext":{},"userEditable":true,"keyField":false,"jsonType":"int","mvEnabled":false,"sqlType":"int4","description":"An integer value used to sort visits in display order.","inputType":"text","name":"study_PhysicalExam_ParticipantVisitVisitDisplayOrder","isReadOnly":false,"isNullable":false,"fieldKeyPath":"study_PhysicalExam_ParticipantVisitVisitDisplayOrder","shortCaption":"Display Order","shownInInsertView":true,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Physical Exam Participant Visit Visit Display Order","nullable":false,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Integer","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true}]},"columnModel":[{"scale":32,"hidden":false,"sortable":true,"align":"left","width":10,"dataIndex":"study_PhysicalExam_ParticipantId","required":true,"editable":false,"header":"Study Physical Exam Participant Id","tooltip":"Subject identifier"},{"scale":15,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"study_PhysicalExam_ParticipantVisitsequencenum","required":true,"editable":false,"header":"Study Physical Exam Participant Visitsequencenum","tooltip":"The sequence number of the visit.  Primarily used in visit-based studies."},{"scale":10,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"study_PhysicalExam_Weight_kg","required":false,"editable":false,"header":"Study Physical Exam Weight Kg"},{"scale":200,"hidden":false,"sortable":true,"align":"left","width":200,"dataIndex":"study_PhysicalExam_ParticipantVisitVisitLabel","required":false,"editable":false,"header":"Study Physical Exam Participant Visit Visit Label","tooltip":"The long/friendly name of each visit."},{"scale":10,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"study_PhysicalExam_ParticipantVisitVisitDisplayOrder","required":true,"editable":false,"header":"Study Physical Exam Participant Visit Visit Display Order","tooltip":"An integer value used to sort visits in display order."}],"measureToColumn":{"Weight_kg":"study_PhysicalExam_Weight_kg","ParticipantId":"study_PhysicalExam_ParticipantId","ParticipantVisit/sequencenum":"study_PhysicalExam_ParticipantVisitsequencenum","ParticipantVisit/Visit/Label":"study_PhysicalExam_ParticipantVisitVisitLabel","ParticipantVisit/Visit/DisplayOrder":"study_PhysicalExam_ParticipantVisitVisitDisplayOrder"},"rows":[{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":2},"study_PhysicalExam_Weight_kg":{"value":86},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249318596","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249318596"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 2"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":3},"study_PhysicalExam_Weight_kg":{"value":84},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249318596","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249318596"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 3"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":4},"study_PhysicalExam_Weight_kg":{"value":83},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249318596","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249318596"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 4"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":7},"study_PhysicalExam_Weight_kg":{"value":80},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249318596","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249318596"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 7"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":9},"study_PhysicalExam_Weight_kg":{"value":79},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249318596","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249318596"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 9"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":10},"study_PhysicalExam_Weight_kg":{"value":79},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249318596","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249318596"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 10"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":10.1},"study_PhysicalExam_Weight_kg":{"value":79},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249318596","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249318596"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 10 Interim"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":11},"study_PhysicalExam_Weight_kg":{"value":78},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249318596","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249318596"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 11"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":12},"study_PhysicalExam_Weight_kg":{"value":77},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249318596","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249318596"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 12"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":13},"study_PhysicalExam_Weight_kg":{"value":75},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249318596","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249318596"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 13"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":0},"study_PhysicalExam_Weight_kg":{"value":55},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320107","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320107"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Baseline"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":2},"study_PhysicalExam_Weight_kg":{"value":54},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320107","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320107"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 2"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":2.1},"study_PhysicalExam_Weight_kg":{"value":52},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320107","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320107"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 2 Interim"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":4},"study_PhysicalExam_Weight_kg":{"value":50},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320107","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320107"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 4"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":8},"study_PhysicalExam_Weight_kg":{"value":51},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320107","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320107"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 8"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":0},"study_PhysicalExam_Weight_kg":{"value":62},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320127","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320127"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Baseline"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":2},"study_PhysicalExam_Weight_kg":{"value":64},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320127","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320127"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 2"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":6},"study_PhysicalExam_Weight_kg":{"value":63},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320127","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320127"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 6"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":7},"study_PhysicalExam_Weight_kg":{"value":65},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320127","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320127"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 7"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":8},"study_PhysicalExam_Weight_kg":{"value":65},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320127","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320127"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 8"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":9},"study_PhysicalExam_Weight_kg":{"value":67},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320127","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320127"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 9"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":10},"study_PhysicalExam_Weight_kg":{"value":69},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320127","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320127"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 10"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":1},"study_PhysicalExam_Weight_kg":{"value":90},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320489","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320489"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 1"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":2},"study_PhysicalExam_Weight_kg":{"value":86},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320489","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320489"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 2"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":5},"study_PhysicalExam_Weight_kg":{"value":84},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320489","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320489"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 5"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":6},"study_PhysicalExam_Weight_kg":{"value":75},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320489","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320489"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 6"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":7},"study_PhysicalExam_Weight_kg":{"value":72},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320489","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320489"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 7"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":1},"study_PhysicalExam_Weight_kg":{"value":73},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320897","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320897"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 1"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":2},"study_PhysicalExam_Weight_kg":{"value":77},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320897","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320897"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 2"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":3},"study_PhysicalExam_Weight_kg":{"value":74},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320897","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320897"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 3"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":4},"study_PhysicalExam_Weight_kg":{"value":75},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320897","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320897"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 4"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":9},"study_PhysicalExam_Weight_kg":{"value":77},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320897","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320897"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 9"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":10},"study_PhysicalExam_Weight_kg":{"value":75},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249320897","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249320897"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 10"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":0},"study_PhysicalExam_Weight_kg":{"value":98},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249325717","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249325717"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Baseline"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":1},"study_PhysicalExam_Weight_kg":{"value":111},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249325717","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249325717"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 1"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":3},"study_PhysicalExam_Weight_kg":{"value":110},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249325717","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249325717"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 3"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":3.1},"study_PhysicalExam_Weight_kg":{"value":112},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249325717","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249325717"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 3 Interim"}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":7},"study_PhysicalExam_Weight_kg":{"value":138},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantId":{"value":"249325717","url":"/study/Internal/Staff/alanv/Visit%20Based%20Study/participant.view?datasetId=5001&participantId=249325717"},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 7"}}],"rowCount":38};
var aggregateData = {"schemaName":"study","queryName":"study-temp-24","formatVersion":9.1,"metaData":{"totalProperty":"rowCount","root":"rows","fields":[{"isVersionField":false,"measure":false,"shownInDetailsView":true,"fieldKeyArray":["GroupId"],"type":"int","displayFieldSqlType":"varchar","fieldKey":"GroupId","ext":{},"userEditable":true,"keyField":false,"jsonType":"int","mvEnabled":false,"sqlType":"int4","displayField":"GroupId/Label","inputType":"text","name":"GroupId","isReadOnly":false,"isNullable":false,"fieldKeyPath":"GroupId","shortCaption":"Category Id","shownInInsertView":true,"isMvEnabled":false,"autoIncrement":false,"caption":"Category Id","nullable":false,"isAutoIncrement":false,"isHidden":false,"dimension":true,"isSelectable":true,"readOnly":false,"friendlyType":"Integer","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":true,"shownInDetailsView":true,"fieldKeyArray":["study_PhysicalExam_ParticipantVisitVisitDisplayOrder"],"type":"int","fieldKey":"study_PhysicalExam_ParticipantVisitVisitDisplayOrder","ext":{},"userEditable":true,"keyField":false,"jsonType":"int","mvEnabled":false,"sqlType":"int4","description":"An integer value used to sort visits in display order.","inputType":"text","name":"study_PhysicalExam_ParticipantVisitVisitDisplayOrder","isReadOnly":false,"isNullable":false,"fieldKeyPath":"study_PhysicalExam_ParticipantVisitVisitDisplayOrder","shortCaption":"Display Order","shownInInsertView":true,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Physical Exam Participant Visit Visit Display Order","nullable":false,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Integer","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":true,"shownInDetailsView":true,"fieldKeyArray":["study_PhysicalExam_ParticipantVisitsequencenum"],"type":"float","fieldKey":"study_PhysicalExam_ParticipantVisitsequencenum","ext":{},"userEditable":true,"keyField":false,"jsonType":"float","mvEnabled":false,"sqlType":"numeric","description":"The sequence number of the visit.  Primarily used in visit-based studies.","inputType":"text","name":"study_PhysicalExam_ParticipantVisitsequencenum","isReadOnly":false,"isNullable":false,"fieldKeyPath":"study_PhysicalExam_ParticipantVisitsequencenum","shortCaption":"Sequencenum","shownInInsertView":true,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Physical Exam Participant Visitsequencenum","nullable":false,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Number (Double)","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":false,"shownInDetailsView":true,"fieldKeyArray":["study_PhysicalExam_ParticipantVisitVisitLabel"],"type":"string","fieldKey":"study_PhysicalExam_ParticipantVisitVisitLabel","ext":{},"userEditable":true,"keyField":false,"jsonType":"string","mvEnabled":false,"sqlType":"varchar","description":"The long/friendly name of each visit.","inputType":"text","name":"study_PhysicalExam_ParticipantVisitVisitLabel","isReadOnly":false,"isNullable":true,"fieldKeyPath":"study_PhysicalExam_ParticipantVisitVisitLabel","shortCaption":"Visit Label","shownInInsertView":true,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Physical Exam Participant Visit Visit Label","nullable":true,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Text (String)","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":true,"isUserEditable":true},{"isVersionField":false,"measure":true,"shownInDetailsView":true,"fieldKeyArray":["AggregateCount"],"type":"int","fieldKey":"AggregateCount","ext":{},"userEditable":false,"keyField":false,"jsonType":"int","mvEnabled":false,"sqlType":"INTEGER","inputType":"text","name":"AggregateCount","isReadOnly":false,"isNullable":true,"fieldKeyPath":"AggregateCount","shortCaption":"Aggregate Count","shownInInsertView":false,"isMvEnabled":false,"autoIncrement":false,"caption":"Aggregate Count","nullable":true,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Integer","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":false,"isUserEditable":false},{"isVersionField":false,"measure":true,"shownInDetailsView":true,"fieldKeyArray":["study_PhysicalExam_Weight_kg"],"type":"int","fieldKey":"study_PhysicalExam_Weight_kg","ext":{},"userEditable":false,"keyField":false,"jsonType":"int","mvEnabled":false,"sqlType":"INTEGER","inputType":"text","name":"study_PhysicalExam_Weight_kg","isReadOnly":false,"isNullable":true,"fieldKeyPath":"study_PhysicalExam_Weight_kg","shortCaption":"Study Physical Exam Weight Kg","shownInInsertView":false,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Physical Exam Weight Kg","nullable":true,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Integer","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":false,"isUserEditable":false},{"isVersionField":false,"measure":true,"shownInDetailsView":true,"fieldKeyArray":["study_PhysicalExam_Weight_kg_STDDEV"],"type":"int","fieldKey":"study_PhysicalExam_Weight_kg_STDDEV","ext":{},"userEditable":false,"keyField":false,"jsonType":"int","mvEnabled":false,"sqlType":"INTEGER","inputType":"text","name":"study_PhysicalExam_Weight_kg_STDDEV","isReadOnly":false,"isNullable":true,"fieldKeyPath":"study_PhysicalExam_Weight_kg_STDDEV","shortCaption":"Study Physical Exam Weight Kg STDDEV","shownInInsertView":false,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Physical Exam Weight Kg STDDEV","nullable":true,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Integer","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":false,"isUserEditable":false},{"isVersionField":false,"measure":true,"shownInDetailsView":true,"fieldKeyArray":["study_PhysicalExam_Weight_kg_STDERR"],"type":"int","fieldKey":"study_PhysicalExam_Weight_kg_STDERR","ext":{},"userEditable":false,"keyField":false,"jsonType":"int","mvEnabled":false,"sqlType":"INTEGER","inputType":"text","name":"study_PhysicalExam_Weight_kg_STDERR","isReadOnly":false,"isNullable":true,"fieldKeyPath":"study_PhysicalExam_Weight_kg_STDERR","shortCaption":"Study Physical Exam Weight Kg STDERR","shownInInsertView":false,"isMvEnabled":false,"autoIncrement":false,"caption":"Study Physical Exam Weight Kg STDERR","nullable":true,"isAutoIncrement":false,"isHidden":false,"dimension":false,"isSelectable":true,"readOnly":false,"friendlyType":"Integer","selectable":true,"isKeyField":false,"hidden":false,"versionField":false,"shownInUpdateView":false,"isUserEditable":false}]},"columnModel":[{"scale":10,"hidden":false,"sortable":true,"align":"left","width":200,"dataIndex":"GroupId","required":true,"editable":false,"header":"Category Id"},{"scale":10,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"study_PhysicalExam_ParticipantVisitVisitDisplayOrder","required":true,"editable":false,"header":"Study Physical Exam Participant Visit Visit Display Order","tooltip":"An integer value used to sort visits in display order."},{"scale":15,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"study_PhysicalExam_ParticipantVisitsequencenum","required":true,"editable":false,"header":"Study Physical Exam Participant Visitsequencenum","tooltip":"The sequence number of the visit.  Primarily used in visit-based studies."},{"scale":200,"hidden":false,"sortable":true,"align":"left","width":200,"dataIndex":"study_PhysicalExam_ParticipantVisitVisitLabel","required":false,"editable":false,"header":"Study Physical Exam Participant Visit Visit Label","tooltip":"The long/friendly name of each visit."},{"scale":0,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"AggregateCount","required":false,"editable":false,"header":"Aggregate Count"},{"scale":0,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"study_PhysicalExam_Weight_kg","required":false,"editable":false,"header":"Study Physical Exam Weight Kg"},{"scale":0,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"study_PhysicalExam_Weight_kg_STDDEV","required":false,"editable":false,"header":"Study Physical Exam Weight Kg STDDEV"},{"scale":0,"hidden":false,"sortable":true,"align":"right","width":60,"dataIndex":"study_PhysicalExam_Weight_kg_STDERR","required":false,"editable":false,"header":"Study Physical Exam Weight Kg STDERR"}],"measureToColumn":{"Weight_kg":"study_PhysicalExam_Weight_kg","ParticipantId":"study_PhysicalExam_ParticipantId","GroupId":"study_ParticipantGroupMap_GroupId","ParticipantVisit/sequencenum":"study_PhysicalExam_ParticipantVisitsequencenum","ParticipantVisit/Visit/Label":"study_PhysicalExam_ParticipantVisitVisitLabel","ParticipantVisit/Visit/DisplayOrder":"study_PhysicalExam_ParticipantVisitVisitDisplayOrder"},"rows":[{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":0},"AggregateCount":{"value":3},"study_PhysicalExam_Weight_kg":{"value":71.66666666666667},"study_PhysicalExam_Weight_kg_STDERR":{"value":13.320827468458726},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Baseline"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":28}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":1},"AggregateCount":{"value":3},"study_PhysicalExam_Weight_kg":{"value":91.33333333333333},"study_PhysicalExam_Weight_kg_STDERR":{"value":10.989894347889692},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 1"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":19.03505538035898}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":2},"AggregateCount":{"value":5},"study_PhysicalExam_Weight_kg":{"value":73.4},"study_PhysicalExam_Weight_kg_STDERR":{"value":6.305553108173778},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 2"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":14.099645385611653}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":2.1},"AggregateCount":{"value":1},"study_PhysicalExam_Weight_kg":{"value":52},"study_PhysicalExam_Weight_kg_STDERR":{"value":null},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 2 Interim"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":null}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":3},"AggregateCount":{"value":3},"study_PhysicalExam_Weight_kg":{"value":89.33333333333333},"study_PhysicalExam_Weight_kg_STDERR":{"value":10.728984626287389},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 3"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":18.58314648635514}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":3.1},"AggregateCount":{"value":1},"study_PhysicalExam_Weight_kg":{"value":112},"study_PhysicalExam_Weight_kg_STDERR":{"value":null},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 3 Interim"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":null}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":4},"AggregateCount":{"value":3},"study_PhysicalExam_Weight_kg":{"value":69.33333333333333},"study_PhysicalExam_Weight_kg_STDERR":{"value":9.938701010583717},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 4"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":17.21433511156714}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":5},"AggregateCount":{"value":1},"study_PhysicalExam_Weight_kg":{"value":84},"study_PhysicalExam_Weight_kg_STDERR":{"value":null},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 5"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":null}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":6},"AggregateCount":{"value":2},"study_PhysicalExam_Weight_kg":{"value":69},"study_PhysicalExam_Weight_kg_STDERR":{"value":5.999999999999999},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 6"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":8.48528137423857}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":7},"AggregateCount":{"value":4},"study_PhysicalExam_Weight_kg":{"value":88.75},"study_PhysicalExam_Weight_kg_STDERR":{"value":16.700174649785353},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 7"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":33.400349299570706}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":8},"AggregateCount":{"value":2},"study_PhysicalExam_Weight_kg":{"value":58},"study_PhysicalExam_Weight_kg_STDERR":{"value":6.999999999999999},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 8"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":9.899494936611665}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":9},"AggregateCount":{"value":3},"study_PhysicalExam_Weight_kg":{"value":74.33333333333333},"study_PhysicalExam_Weight_kg_STDERR":{"value":3.7118429085533484},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 9"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":6.429100507328637}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":10},"AggregateCount":{"value":3},"study_PhysicalExam_Weight_kg":{"value":74.33333333333333},"study_PhysicalExam_Weight_kg_STDERR":{"value":2.9059326290271157},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 10"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":5.033222956847166}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":10.1},"AggregateCount":{"value":1},"study_PhysicalExam_Weight_kg":{"value":79},"study_PhysicalExam_Weight_kg_STDERR":{"value":null},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 10 Interim"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":null}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":11},"AggregateCount":{"value":1},"study_PhysicalExam_Weight_kg":{"value":78},"study_PhysicalExam_Weight_kg_STDERR":{"value":null},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 11"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":null}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":12},"AggregateCount":{"value":1},"study_PhysicalExam_Weight_kg":{"value":77},"study_PhysicalExam_Weight_kg_STDERR":{"value":null},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 12"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":null}},{"study_PhysicalExam_ParticipantVisitsequencenum":{"value":13},"AggregateCount":{"value":1},"study_PhysicalExam_Weight_kg":{"value":75},"study_PhysicalExam_Weight_kg_STDERR":{"value":null},"GroupId":{"value":44,"displayValue":"Everyone"},"study_PhysicalExam_ParticipantVisitVisitDisplayOrder":{"value":0},"study_PhysicalExam_ParticipantVisitVisitLabel":{"value":"Month 13"},"study_PhysicalExam_Weight_kg_STDDEV":{"value":null}}],"rowCount":17};

var errorPointLayer = new LABKEY.vis.Layer({
    name: "Weight (kg)",
    geom: new LABKEY.vis.Geom.Point(),
    data: aggregateData.rows,
    aes: {
        color: function(row){return row.GroupId.displayValue;},
        hoverText: function(row){return row.GroupId.displayValue + ' Temperature, day ' + row.study_PhysicalExam_ParticipantVisitsequencenum.value + ', ' + row.study_PhysicalExam_Weight_kg.value;}
    }
});

var errorPathLayer = new LABKEY.vis.Layer({
    name: "Weight (kg)",
    geom: new LABKEY.vis.Geom.Path(),
    data: aggregateData.rows,
    aes: {
        group: function(row){return row.GroupId.displayValue;},
        color: function(row){return row.GroupId.displayValue;}
    }
});

var errorBarLayer = new LABKEY.vis.Layer({
    name: "Weight (kg)",
    geom: new LABKEY.vis.Geom.ErrorBar(),
    data: aggregateData.rows,
    aes: {
        error: function(row){return row.study_PhysicalExam_Weight_kg_STDDEV.value},
//        error: function(row){return row.study_PhysicalExam_Weight_kg_STDERR.value},
        color: function(row){return row.GroupId.displayValue},
        yLeft: function(row){return row.study_PhysicalExam_Weight_kg.value;}
    }
});

var individualPointLayer = new LABKEY.vis.Layer({
    name: "Weight (kg)",
    geom: new LABKEY.vis.Geom.Point(),
    aes: {
        color: function(row){return row.study_PhysicalExam_ParticipantId.value},
        hoverText: function(row){return row.study_PhysicalExam_ParticipantId.value + ' Temperature, day ' + row.study_PhysicalExam_ParticipantVisitsequencenum.value + ', ' + row.study_PhysicalExam_Weight_kg.value;}
    }
});

var individualPathLayer = new LABKEY.vis.Layer({
    name: "Weight (kg)",
    geom: new LABKEY.vis.Geom.Path(),
    aes: {
        color: function(row){return row.study_PhysicalExam_ParticipantId.value},
        group: function(row){return row.study_PhysicalExam_ParticipantId.value}
    }
});

var errorPlotConfig = {
    renderTo: 'errorChart',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Testing error bar geom'},
        yLeft: {value: 'Temperature (C)'},
        x: {value: 'Sequence Number'}
    },
    data: aggregateData.rows,
//    data: individualData.rows,
    layers: [/*individualPathLayer, individualPointLayer,*/ errorPathLayer, errorBarLayer, errorPointLayer],
    aes: {
        yLeft: function(row){
            if(row.study_PhysicalExam_Weight_kg.value < 40){
                console.log(row.study_PhysicalExam_Weight_kg.value);
            }
            return row.study_PhysicalExam_Weight_kg.value;
        },
        x: function(row){return row.study_PhysicalExam_ParticipantVisitsequencenum.value}
    },
    scales: {
        x: {
            scaleType: 'continuous',
			trans: 'linear',
            tickFormat: function(value){
                if(value > 0) {
                    return "Day " + value;
                } else {
                    return "Baseline";
                }
            }
        },
        yLeft: {
            scaleType: 'continuous',
			trans: 'linear'
        },
        color: {
            scaleType: 'discrete'
        }
    }
};
var errorPlot = new LABKEY.vis.Plot(errorPlotConfig);

var coffeeData = [
    {"person":"Alan","time":"9:30","consumedCoffee":"No Coffee","efficiency":65},{"person":"Alan","time":"10:00","consumedCoffee":"Coffee","efficiency":85},
    {"person":"Alan","time":"10:30","consumedCoffee":"No Coffee","efficiency":82},{"person":"Alan","time":"11:00","consumedCoffee":"No Coffee","efficiency":83},
    {"person":"Alan","time":"11:30","consumedCoffee":"No Coffee","efficiency":78},{"person":"Alan","time":"12:00","consumedCoffee":"No Coffee","efficiency":72},
    {"person":"Alan","time":"12:30","consumedCoffee":"No Coffee","efficiency":69},{"person":"Alan","time":"1:00","consumedCoffee":"No Coffee","efficiency":62},
    {"person":"Alan","time":"1:30","consumedCoffee":"Coffee","efficiency":88},{"person":"Alan","time":"2:00","consumedCoffee":"No Coffee","efficiency":85},
    {"person":"Alan","time":"2:30","consumedCoffee":"No Coffee","efficiency":82},{"person":"Alan","time":"3:00","consumedCoffee":"No Coffee","efficiency":84},
    {"person":"Alan","time":"3:30","consumedCoffee":"No Coffee","efficiency":79},{"person":"Alan","time":"4:00","consumedCoffee":"No Coffee","efficiency":78},
    {"person":"Alan","time":"4:30","consumedCoffee":"No Coffee","efficiency":null},{"person":"Alan","time":"5:00","consumedCoffee":"No Coffee","efficiency":76},
    {"person":"Josh Extra Super Duper Long Name","time":"9:30","consumedCoffee":"No Coffee","efficiency":300},{"person":"Josh Extra Super Duper Long Name","time":"10:00","consumedCoffee":"No Coffee","efficiency":300},
    {"person":"Josh Extra Super Duper Long Name","time":"10:30","consumedCoffee":"No Coffee","efficiency":300},{"person":"Josh Extra Super Duper Long Name","time":"11:00","consumedCoffee":"No Coffee","efficiency":299},
    {"person":"Josh Extra Super Duper Long Name","time":"11:30","consumedCoffee":"No Coffee","efficiency":297},{"person":"Josh Extra Super Duper Long Name","time":"12:00","consumedCoffee":"No Coffee","efficiency":300},
    {"person":"Josh Extra Super Duper Long Name","time":"12:30","consumedCoffee":"No Coffee","efficiency":300},{"person":"Josh Extra Super Duper Long Name","time":"1:00","consumedCoffee":"No Coffee","efficiency":296},
    {"person":"Josh Extra Super Duper Long Name","time":"1:30","consumedCoffee":"No Coffee","efficiency":300},{"person":"Josh Extra Super Duper Long Name","time":"2:00","consumedCoffee":"No Coffee","efficiency":300},
    {"person":"Josh Extra Super Duper Long Name","time":"2:30","consumedCoffee":"No Coffee","efficiency":298},{"person":"Josh Extra Super Duper Long Name","time":"3:00","consumedCoffee":"No Coffee","efficiency":295},
    {"person":"Josh Extra Super Duper Long Name","time":"3:30","consumedCoffee":"No Coffee","efficiency":294},{"person":"Josh Extra Super Duper Long Name","time":"4:00","consumedCoffee":"No Coffee","efficiency":295},
    {"person":"Josh Extra Super Duper Long Name","time":"4:30","consumedCoffee":"No Coffee","efficiency":297},{"person":"Josh Extra Super Duper Long Name","time":"5:00","consumedCoffee":"No Coffee","efficiency":296}
];

var coffeePointLayer = new LABKEY.vis.Layer({
    name: "Efficiency",
    geom: new LABKEY.vis.Geom.Point(),
    aes: {
        color: 'person',
        shape: 'consumedCoffee',
        hoverText: function(row){return 'Person: ' + row.person + "\n" + row.consumedCoffee + " Consumed \nEfficiency: " + row.efficiency}
    }
});

var coffeePathLayer = new LABKEY.vis.Layer({
    name: "Efficiency",
    geom: new LABKEY.vis.Geom.Path({color: '#66c2a5'}),
    aes: {
        color: 'person',
        group: 'person'
    }
});

var coffeePlot = new LABKEY.vis.Plot({
    renderTo: 'coffeePlot',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Efficiency (%) Over Time'},
        x: {value: 'Efficiency (%)'},
        yLeft: {value: 'Time (PST)'}
    },
    data: coffeeData,
    layers: [coffeePathLayer, coffeePointLayer],
    aes: {
        x: 'time',
        yLeft: 'efficiency'
    },
    scales: {
        x: {
            scaleType: 'discrete'
        },
        yLeft: {
            scaleType: 'continuous',
			trans: 'linear',
            min: 0
        }
    }
});

var boxPlotData = [];
var medianLineData = [];
var groupedBoxData = null;

for(var i = 0; i < 6; i++){
    var group = "Really Long Group Name "+(i+1);
    for(var j = 0; j < 25; j++){
        boxPlotData.push({
            group: group,
            age: parseInt(25+(Math.random()*(55-25))), //Compute a random age between 25 and 55
            gender: parseInt((Math.random()*2)) === 0 ? 'male' : 'female'
        });
    }
    for(j = 0; j < 3; j++){
        boxPlotData.push({
            group: group,
            age: parseInt(75+(Math.random()*(95-75))), //Compute a random age between 75 and 95
            gender: parseInt((Math.random()*2)) === 0 ? 'male' : 'female'
        });
    }
    for(j = 0; j < 3; j++){
        boxPlotData.push({
            group: group,
            age: parseInt(1+(Math.random()*(16-1))), //Compute a random age between 1 and 16
            gender: parseInt((Math.random()*2)) === 0 ? 'male' : 'female'
        });
    }
}

groupedBoxData = LABKEY.vis.groupData(boxPlotData, function(row){return row.group});

for(var groupName in groupedBoxData){
    var stats = LABKEY.vis.Stat.summary(groupedBoxData[groupName], function(row){return row.age});
    medianLineData.push({x: groupName, y:stats.Q2, color: 'median'});
}

var boxLayer = new LABKEY.vis.Layer({
    geom: new LABKEY.vis.Geom.Boxplot({
        position: 'jitter',
//        color: 'teal',
//        fill: '#FFFF00',
        outlierOpacity: '1',
        outlierFill: 'red',
        showOutliers: true
//        showOutliers: false
//        opacity: '.5'
//        lineWidth: 3
    }),
    aes: {
        hoverText: function(x, stats){
            return x + ':\nMin: ' + stats.min + '\nMax: ' + stats.max + '\nQ1: ' + stats.Q1 + '\nQ2: ' + stats.Q2 +
                    '\nQ3: ' + stats.Q3;
        },
        outlierHoverText: function(row){return "Group: " + row.group + ", Age: " + row.age;},
        outlierColor: function(row){return "outlier";},
        outlierShape: function(row){return row.gender;}
    }
});

var boxPointLayer = new LABKEY.vis.Layer({
    geom: new LABKEY.vis.Geom.Point({
        position: 'jitter',
        color: 'orange',
        opacity: .6,
        size: 3
    }),
    aes: {
        hoverText: function(row){return row.group + ", Age: " + row.age;}
    }
});

var medianLineLayer = new LABKEY.vis.Layer({
    geom: new LABKEY.vis.Geom.Path({size: 2}),
    aes: {x: 'x', y: 'y', color: 'color', group: 'color'},
    data: medianLineData
});

var boxPlot = new LABKEY.vis.Plot({
    renderTo: 'box',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Example Box Plot'},
        yLeft: {value: 'Age'},
        x: {value: 'Groups of People'}
    },
//    data: labResultsRows,
    data: boxPlotData,
    layers: [boxLayer, medianLineLayer/*, boxPointLayer*/],
    aes: {
        yLeft: 'age',
        x: 'group'
//        yLeft: function(row){return row.study_LabResults_CD4.value},
//        x: function(row){return "All Participants"}
//        x: function(row){return row.study_LabResults_ParticipantId.value}
    },
    scales: {
        x: {
            scaleType: 'discrete'
        },
        yLeft: {
            scaleType: 'continuous',
            trans: 'linear'
        }
    },
    margins: {
        bottom: 75
    }
});

var discreteScatter = new LABKEY.vis.Plot({
    renderTo: 'discreteScatter',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Scatterplot With Jitter'},
        yLeft: {value: 'Age'},
        x: {value: 'Groups of People'}
    },
    data: boxPlotData,
    layers: [new LABKEY.vis.Layer({
        geom: new LABKEY.vis.Geom.Point({
            position: 'jitter',
            color: 'teal',
            size: 3
        })
    })],
    aes: {
        yLeft: 'age',
        x: 'group',
        color: 'group'
    },
    scales: {
        x: {
            scaleType: 'discrete'
        },
        yLeft: {
//            scaleType: 'discrete',
//            domain: ['10', '20', '30', '40', '50', '60', '70', '80', '90']
            scaleType: 'continuous',
            trans: 'linear'
        }
    },
    margins: {
        bottom: 75
    }
});

var scatterData = [];
for(var i = 0; i < 1000; i++){
    var point = {
        x: i % 9 == 0 ? null : parseInt((Math.random()*(150))),
        y: Math.random() * 1500,
        z: parseInt(Math.random()*125)
    };
    scatterData.push(point);
}

scatterData.push({
    x: 60,
    y: .001,
    z: 150
});

var scatterPlot = new LABKEY.vis.Plot({
    renderTo: 'scatter',
    width: 900,
    height: 700,
    clipRect: false,
    labels: {
        main: {
            value:'Scatter With Null Points & Size Scale',
            lookClickable: true,
            listeners: {
                click: function(){console.log("Main Label clicked!")}
            }
        },
        x: {
            value: "X Axis",
            lookClickable: true,
            listeners: {
                click: function(){console.log("Clicking the X Axis!")}
            }
        },
        y: {
            value:"Y Axis",
            lookClickable: true
        }
    },
    layers: [new LABKEY.vis.Layer({
        data: scatterData,
        geom: new LABKEY.vis.Geom.Point({
            plotNullPoints: true,
            size: 2,
            opacity: .5,
            color: '#FF33FF'
        }),
        aes: {x:'x', y: 'y', size: 'z'}
    })],
    scales: {
        y: {scaleType: 'continuous', trans: 'log'},
        size: {scaleType: 'continuous', trans: 'linear', range: [1, 10]}
    }
});

var colorScatter = new LABKEY.vis.Plot({
    renderTo: 'colorScatter',
    width: 900,
    height: 700,
    clipRect: false,
    labels: {
        main: {
            value:'Scatter With Continuous Color Scale'
        },
        x: {
            value: "X Axis"
        },
        y: {
            value:"Y Axis"
        }
    },
    layers: [new LABKEY.vis.Layer({
        data: scatterData,
        geom: new LABKEY.vis.Geom.Point(),
        aes: {x:'x', y: 'y', color: 'y'}
    })],
    scales: {
        y: {scaleType: 'continuous', trans: 'linear'},
        color: {scaleType: 'continuous', trans: 'linear', range: ['#660000', '#FF6666']}
    }
});

var statFnPlot = new LABKEY.vis.Plot({
    renderTo: 'statFn',
    width: 900,
    height: 300,
    clipRect: false,
    labels: {
        main: {value: 'Line Plot with LABKEY.vis.Stat.fn'}
    },
    layers: [new LABKEY.vis.Layer({
        geom: new LABKEY.vis.Geom.Path({color: '#8ABEDE'})
    }), new LABKEY.vis.Layer({
        geom: new LABKEY.vis.Geom.Point({color: '#8ABEDE'}),
        aes: {hoverText: function(row){return row.x;}}
    })],
    data: LABKEY.vis.Stat.fn(function(x){return Math.log(x) * 2;}, 20, 1, 15),
    aes: {x: 'x', y: 'y'}
});

plot.render();
boxPlot.render();
errorPlot.render();
coffeePlot.render();
discreteScatter.render();
scatterPlot.render();
colorScatter.render();
statFnPlot.render();
renderStats();
