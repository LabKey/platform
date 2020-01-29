# LabKey Experiment Module

The Experiment module defines the XAR (eXperimental 
ARchive) file format for importing and exporting experiment data and 
annotations, and allows user-defined custom annotations for specialized 
protocols and data.

## Developing app pages

This module is setup to use `webpack` to build client application pages, mostly for developing with `React` 
components. The artifacts will be generated and placed into the standard LabKey view locations to make 
the pages available to the server through the `experiment` controller. The app HTML and view.xml files 
will get created in the `experiment/resources/views` directory and the JS/CSS artifacts will get 
created in the `experiment/resources/web/experiment/gen` directory.

### Client artifact building

You can install the necessary npm packages and build the module by running the standard module
gradlew tasks, `./gradlew deployApp` or `./gradlew :server:modules:experiment:deployModule`
or you can run one of the following npm commands directly from the `experiment` directory:
```
npm run setup
npm run build
```

To clean the generated client-side artifacts from the module:
```
npm run clean
```

### Adding an app page

To add a new application page to the generated artifacts:
1. Create a new dir for your client code and React components at `experiment/src/client/<APP PAGE NAME>`.
1. Add a new entry point definition to the `experiment/webpack/entryPoints.js` file. For that new entry 
point, set the `name=<action name for the app page>`, `title=<page title>`, `permission=<view.xml perm class>`,
and `path=<app code path>`.
1. In your `src/client/<APP PAGE NAME>` dir, create an `app.tsx` file and a `dev.tsx` file based on
an example from one of the existing app pages.
1. Add your main app `React component` and any other components, models, actions, etc.
1. Run one of the build commands from the section above and verify that your new app generated files
are created.


### Develop with hot reloading

For incremental builds of all client-side resources when a file changes, you can run the dev 
build with a file watcher from the `<labke>/server/modules/experiment` directory (NOTE: to use this, 
the server action is `experiment-appDev.view` instead of the normal `experiment-app.view`, where 
`app` is your application page name from step #2 above). To start dev mode hot reloading run:
```
npm start
```  