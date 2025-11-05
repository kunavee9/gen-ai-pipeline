
def call(){
    try{
        println "[STATUS] Initiating Trivy Scan Process!"

        def configFile = readJSON file: "${env.WORKSPACE}/pipelineconfig.json"
        def appname = configFile."appname".toLowerCase(), technology = configFile."technology".toLowerCase(), buildType = configFile."build"."buildType".toLowerCase()

        def trivy_command

        if(buildType == "npm"){
            trivy_command = "sudo trivy fs . --format json --output sast_report.json --include-dev-deps"
        }
        else{
            error "[ERROR] Build Type Not Defined; Expected values: 'npm'; Received: ${buildType}"
        }

        println "Executing: ${trivy_command}"

        def execute_trivy = (env.agent_os == "windows") ? bat(script: trivy_command, returnStdout: true) : sh(script: trivy_command, returnStdout: true)

        println "[STATUS] Trivy Scan Execution Complete!"
    }
    catch(Exception e){
        currentBuild.result = 'FAILURE';
        println "[ERROR] An unexpected error occurred while executing ci_scanTrivy.groovy as part of Trivy Scan stage: ${e.message}"
        error "[ERROR] CICD Pipeline Execution Failed!"
    }
}
