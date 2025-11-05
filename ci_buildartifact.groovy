
def call(){
    try{
        println "[STATUS] Initiating Build Process!"

        def configFile = readJSON file: "${env.WORKSPACE}/pipelineconfig.json"
        def appname = configFile."appname".toLowerCase(), technology = configFile."technology".toLowerCase(), buildType = configFile."build"."buildType".toLowerCase()

        generate_immutable_version()    // Defines Immutable Artifact Version
        
        // Build execution based on Build Type for Application
        if(buildType == "npm"){
            cicd_build_npm()
        }
        else{
            error "[ERROR] Build Type Not Defined; Expected values: 'npm'; Received: ${buildType}"
        }
    }
    catch(Exception e){
        currentBuild.result = 'FAILURE';
        println "[ERROR] An unexpected error occurred while executing ci_buildArtifact.groovy as part of Build Artifact stage: ${e.message}"
        error "[ERROR] CICD Pipeline Execution Failed!"
    }
}


// Function to execute build for npm based applications
def cicd_build_npm(){
    def configFile = readJSON file: "${env.WORKSPACE}/pipelineconfig.json"
    def appname = configFile."appname".toLowerCase(), technology = configFile."technology".toLowerCase(), buildDirectory = configFile."build"."buildDirectory"

    try{
        println "[STATUS] NPM Build Execution In-Progress for Application: ${appname} Technology: ${technology}"

        def build_command = "npm install && CI=false npm run build"
        println "Executing: ${build_command}"

        def execute_build = (env.agent_os == "windows") ? bat(script: build_command, returnStdout: true) : sh(script: build_command, returnStdout: true)

        println "[STATUS] NPM process complete!"

        def create_artifact_command = "cd ${buildDirectory} && sudo tar -cvf ../${appname}-${env.ARTIFACT_VERSION}.tar *"
        println "Executing: ${create_artifact_command}"

        def create_artifact = (env.agent_os == "windows") ? bat(script: create_artifact_command, returnStdout: true) : sh(script: create_artifact_command, returnStdout: true)

        println "[STATUS] Build Complete! Artifact created successfully: ${appname}-${env.ARTIFACT_VERSION}.tar"
    }
    catch(Exception e){
        currentBuild.result = 'FAILURE';
        println "[ERROR] An unexpected error occurred while executing cicd_build_npm() from ci_buildArtifact.groovy as part of Build Artifact stage: ${e.message}"
        error "[ERROR] CICD Pipeline Execution Failed!"
    }
}



// Function to generate immutable version for Artifacts
def generate_immutable_version(){
    try{
        def configFile = readJSON file: "${env.WORKSPACE}/pipelineconfig.json"
        def buildType = configFile."build"."buildType".toLowerCase()

        def packageFile, majorVersion

        if(buildType == "npm"){
            packageFile = readJSON file: "${env.WORKSPACE}/package.json"
            majorVersion = packageFile."version"
        }
        else{
            error "[ERROR] Build Type Not Defined; Expected values: 'npm'; Received: ${buildType}"
        }

        def git_command = "git rev-parse --short HEAD"

        def commitId = (env.agent_os == "windows") ? bat(script: git_command, returnStdout: true).trim().toString() : sh(script: git_command, returnStdout: true).trim().toString()

        env.ARTIFACT_VERSION = "${majorVersion}-${commitId}"
    }
    catch(Exception e){
        currentBuild.result = 'FAILURE';
        println "[ERROR] An unexpected error occurred while executing generate_immutable_version() from ci_buildArtifact.groovy as part of Build Artifact stage: ${e.message}"
        error "[ERROR] CICD Pipeline Execution Failed!"
    }
}
