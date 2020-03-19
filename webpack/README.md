# LabKey Module React Page Development

The platform repository has been setup so that it contains the shared configurations needed to develop
[React] pages within a LabKey module. These shared configurations include [webpack] configurations for 
building both production and development mode LabKey React Pages in a standard way as well as the 
`package.json devDependencies` that those build steps rely on. This removes the need for each LabKey 
module to redefine those pieces when adding a new LabKey React page. 

Note that if build customizations are needed for a given module, they can opt out of these shared 
configurations by setting up their own webpack config files and changing their build scripts in the 
relevant module's `package.json` file.  

### How it works

Each new LabKey React page `entryPoint` will be defined within the module where the relevant actions 
and code will live. Each `entryPoint` will get a set of files generated as part of the 
[LabKey Gradle build] steps for that module. The artifacts will be generated and placed into the 
standard LabKey `views` directory to make the pages available to the server through the module's 
controller. The generated `<entryPoint>.html` and `<entryPoint>.view.xml` files will be placed in the 
`<module>/resources/views` directory and the generated JS/CSS artifacts will be placed in the 
`<module>/resources/web/<module>/gen` directory. 

### Setting up a LabKey module

To configure a LabKey module to participant in the React page build process and use the shared 
configurations from the platform repository:
1. Add the following files to your module's main directory: 
    1. `package.json` - Defines your module's npm build scripts and npm package dependencies.
        Note that after your first successful build of this module after adding this,
        a new `package-lock.json` file will be generated. You will want to add that file to your git repo
        and check it in as well. Note that in this file the `LK_MODULE` value will need to be set 
        to match your module name and the `npm clean` command might need to be adjusted if your module
        already has file in the `resources/views` directory.
    1. `.npmrc` - Defines the Artifactory registry path for the `@labkey` scope, if you 
        plan to use any of the Labkey npm packages for your page.
    1. `tsconfig.json` - Typescript configuration file that extends the `tsconfig.json` file at the
        platform level. This will ensure that your module's `node_modules` and `resources` directories
        are excluded during the client-side build process.
    1. `README.md` - Add your own README file for your module and have it point back to this page
        for the steps in the "Adding a new entryPoint" section of this document.
1. Create the `<module>/src/client` directories and add a file named `entryPoints.js`, more on this in
    the "Adding a new entryPoint" section of this doc.
1. Update your module's `build.gradle` file to add a line so that it's `npmInstall` command is dependent
    on the `npmInstall` command finishing at the platform repository level. See example at 
    `platform/experiment/build.gradle`. 
1. Update the `platform/.gitignore` file so that it knows to ignore your module's `node_modules` directory
    and generated JS/CSS artifacts.

You can see examples of each of these files in the following LabKey modules: 
[assay], [experiment], and [list].

### Building LabKey React pages

You can install the necessary npm packages and build the module by running the standard module
gradlew tasks, `./gradlew deployApp` or `./gradlew :server:modules:platform:<module>>:deployModule`. 
You can also run one of the following npm commands directly from the module's main directory:
```
npm run setup
npm run build
```

To clean the generated client-side artifacts from the module:
```
npm run clean
```

### Adding a new entryPoint

To add a new `entryPoint` for a LabKey React page:
1. Create a new dir for your client code and React components at `<module>/src/client/<ENTRYPOINT_NAME>`.
1. Add a new entry point definition to the `<module>/src/client/entryPoints.js` file. This will allow
    for the client-side build process to pickup your new files and generated the relevant artifactos.
    For the new entry point, set the following properties:
    1. `name=<action name for the entryPoint page>`
    1. `title=<page title>`
    1. `permission=<view.xml perm class>`
    1. `path=<entryPoint code path from step #1>`
1. In your `src/client/<ENTRYPOINT_NAME>` dir, create an `app.tsx` file and a `dev.tsx` file based on
    an example from one of the existing app pages. Add your main app React component file, 
    `<ENTRYPOINT_NAME>.tsx`, and any other components, models, actions, 
    etc. in the `<module>/src/client/<ENTRYPOINT_NAME>` directory.
1. Update the `platform/.gitignore` file for your new entrypoint's generated views / files
1. Run the `./gradlew deployModule` command for your module and verify that your new generated files
    are created in your module's `resources` directory.

### Developing with Hot Module Reloading (HMR)

To allow updates made to TypeScript, JavaScript, CSS, and SCSS files to take effect on your LabKey
React page without having to manually build the changes each time, you can develop with Hot Module 
Reloading enabled via a webpack development server. You can run the HMR server from the 
`trunk/server/modules/platform/<module>` directory via the `npm start` command. Once started, you 
will need to access your page via an alternate action name to view the changes. The server action 
is `module-entryPointDev.view` instead of the normal `module-entryPoint.view`.

Note that since modules in the platform repository share configurations for the webpack development
server, they are set to us the same port number for the HMR environment. This means that you can only
have one module's HMR mode enabled at a time. If you try to run `npm start` for a second module, you
will get an error message saying that the `address is already in use`.
 
```
cd trunk/server/modules/platform/<module>
npm start
```  

[React]: https://reactjs.org
[webpack]: https://webpack.js.org/
[LabKey Gradle build]: https://www.labkey.org/Documentation/wiki-page.view?name=gradleBuild
[assay]: https://github.com/LabKey/platform/tree/develop/assay
[experiment]: https://github.com/LabKey/platform/tree/develop/experiment
[list]: https://github.com/LabKey/platform/tree/develop/list   
