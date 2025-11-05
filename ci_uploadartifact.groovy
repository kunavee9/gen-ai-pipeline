
def call(){
    try{
        println "[STATUS] Preparing for Artifact Upload!"

        def configFile = readJSON file: "${env.WORKSPACE}/pipelineconfig.json"
        def appname = configFile."appname".toLowerCase(), technology = configFile."technology".toLowerCase(), buildType = configFile."build"."buildType".toLowerCase(), artifactory = configFile."build"."artifactory".toLowerCase()

        if(artifactory == "jfrog"){
            def jfrog_repository = (env.JOB_URL.toUpperCase().contains("TOPIC") || env.JOB_URL.toUpperCase().contains("FEATURE")) ? "snapshot-artifacts" : "release-artifacts"

            def package_name = (buildType == "npm") ? "${appname}-${env.ARTIFACT_VERSION}.tar" : ""

            def jfrog_command = "jf rt u ${package_name} ${jfrog_repository}/${technology}/${appname}/"
            println "Executing: ${jfrog_command}"
            def execute_jfrog = (env.agent_os == "windows") ? bat(script: jfrog_command, returnStdout: true) : sh(script: jfrog_command, returnStdout: true)

            // Feature Available only for Pro versions of JFrog Artifactory
            //def jfrog_set_props = """jf rt sp \"${jfrog_repository}/${technology}/${appname}/${package_name}\" \"SCA_QUALITY_GATE=${env.SCA_QUALITY_GATE};SAST_QUALITY_GATE=${env.SAST_QUALITY_GATE};CI_QUALITY_GATE=${env.FINAL_QUALITY_GATE_STATUS}\""""
            //println "Setting properties: ${jfrog_set_props}"
            //def execute_set_props = (env.agent_os == "windows") ? bat(script: jfrog_set_props, returnStdout: true) : sh(script: jfrog_set_props, returnStdout: true)


            println "[STATUS] Artifact Upload Complete: http://IP:8082/ui/repos/tree/General/${jfrog_repository}/${technology}/${appname}/${package_name}"
        }
        else{
            error "[ERROR] Artifactory Type Not Defined; Expected values: 'jfrog'; Received: ${artifactory}"
        }
    }
    catch(Exception e){
        currentBuild.result = 'FAILURE';
        println "[ERROR] An unexpected error occurred while executing ci_uploadArtifact.groovy as part of Upload Artifact stage: ${e.message}"
        error "[ERROR] CICD Pipeline Execution Failed!"
    }
}
