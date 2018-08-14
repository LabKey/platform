// Keep up to date with schemas/etl.xsd
var tags = {
    etl: {
        attrs: {
            xmlns: null,
            standalone: ["true", "false"],
            siteScope: ["true", "false"],
            allowMultipleQueuing: ["true", "false"],
            transactSourceSchema: null,
            transactDestinationSchema: null
        },
        children: ["name", "description", "transforms", "schedule", "incrementalFilter", "parameters", "pipelineParameters", "constants"]
    },
    name: {
        attrs: {},
        children: []
    },
    description: {
        attrs: {},
        children: []
    },
    transforms: {
        attrs: {},
        children: ["transform"]
    },
    transform: {
        attrs: {
            id: null,
            type: null,
            externalTaskId: null,
            parentPipelineTaskId: null,
            saveState: ["true", "false"]
        },
        children: ["description", "source", "destination", "procedure", "taskref"]
    },
    source: {
        attrs: {
            schemaName: null,
            queryName: null,
            timestampColumnName: null,
            runColumnName: null,
            remoteSource: null,
            sourceTimeout: null,
            useTransaction: ["true", "false"],
            containerFilter: ["Current", "CurrentWithUser", "CurrentAndFirstChildren", "CurrentAndSubfolders", "CurrentAndSiblings", "CurrentOrParentAndWorkbooks", "CurrentPlusProject", "CurrentAndParents", "CurrentPlusProjectAndShared", "AssayLocation", "WorkbookAndParent", "StudyAndSourceStudy", "AllFolders"]
        },
        children: []
    },
    destination: {
        attrs: {
            schemaName: null,
            queryName: null,
            bulkLoad: ["true", "false"],
            dir: null,
            fileBaseName: null,
            columnDelimiter: null,
            quote: null,
            rowDelimiter: null,
            fileExtension: null,
            targetOptionType: ["marge", "append", "truncate"],
            type: ["query", "file"],
            useTransaction: ["true", "false"],
            batchSize: null,
            batchColumn: null
        },
        children: []
    },
    procedure: {
        attrs: {
            schemaName: null,
            procedureName: null,
            useTransaction: ["true", "false"]
        },
        children: ["procedureParameter"]
    },
    procedureParameter: {
        attrs: {
            name: null,
            value: null,
            override: ["true", "false"],
            scope: ["local", "global"],
            noWorkValue: null
        },
        children: []
    },
    taskref: {
        attrs: {
            name: null,
            ref: null
        },
        children: []
    },
    schedule: {
        attrs: {},
        children: ["poll", "chron"]
    },
    poll: {
        attrs: {
            interval: null
        },
        children: []
    },
    chron: {
        attrs: {
            expression: null
        },
        children: []
    },
    incrementalFilter: {
        attrs: {
            className: ["RunFilterStrategy", "ModifiedSinceFilterStrategy", "SelectAllFilterStrategy"],
            timestampColumnName: null,
            runTableSchema: null,
            runTable: null,
            pkColumnName: null,
            fkColumnName: null
        },
        children: ["deletedRowsSource"]
    },
    deletedRowsSource: {
        attrs: {
            deletedSourceKeyColumnName: null,
            targetKeyColumnName: null
        },
        children: []
    },
    paramters: {
        attrs: {},
        children: ["parameter"]
    },
    parameter: {
        attrs: {
            name: null,
            value: null,
            type: ["BIGINT","bigint","BINARY","binary","BOOLEAN","boolean","CHAR","char","DECIMAL","decimal","DOUBLE","double","INTEGER","integer","LONGVARBINARY","longvarbinary","LONGVARCHAR","longvarchar","REAL","real","SMALLINT","smallint","DATE","date","TIME","time","TIMESTAMP","timestamp","TINYINT","tinyint","VARBINARY","varbinary","VARCHAR","varchar","GUID","guid","NULL","null","OTHER","other"]
        },
        children: []
    },
    pipelineParameters: {
        attrs: {},
        children: ["pipelineParameter"]
    },
    pipelineParameter: {
        attrs: {
            name: null,
            value: null
        },
        children: []
    },
    constants: {
        attrs: {},
        children: ["parameter"]
    }
};

function completeAfter(cm, pred) {
    var cur = cm.getCursor();
    if (!pred || pred()) setTimeout(function() {
        if (!cm.state.completionActive)
            cm.showHint({completeSingle: false});
    }, 100);
    return CodeMirror.Pass;
}

function completeIfAfterLt(cm) {
    return completeAfter(cm, function() {
        var cur = cm.getCursor();
        return cm.getRange(CodeMirror.Pos(cur.line, cur.ch - 1), cur) === "<";
    });
}

function completeIfInTag(cm) {
    return completeAfter(cm, function() {
        var tok = cm.getTokenAt(cm.getCursor());
        if (tok.type === "string" && (!/['"]/.test(tok.string.charAt(tok.string.length - 1)) || tok.string.length === 1)) return false;
        var inner = CodeMirror.innerMode(cm.getMode(), tok.state).state;
        return inner.tagName;
    });
}