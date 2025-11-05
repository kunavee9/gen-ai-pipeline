
def call(){
    withSonarQubeEnv(credentialsId: "sonarqube_automation_user"){
        try{
            println "[STATUS] Initiating Sonar Scan Process!"

            def configFile = readJSON file: "${env.WORKSPACE}/pipelineconfig.json"
            def appname = configFile."appname".toLowerCase(), technology = configFile."technology".toLowerCase(), buildType = configFile."build"."buildType".toLowerCase()
            def sonarProjectKey = configFile."qualitygates"."sonarProjectKey"

            def sonar_command

            if(buildType == "npm"){
                sonar_command = "sudo npm run sonar"
            }
            else{
                error "[ERROR] Build Type Not Defined; Expected values: 'npm'; Received: ${buildType}"
            }

            println "Executing: ${sonar_command}"

            def execute_sonar = (env.agent_os == "windows") ? bat(script: sonar_command, returnStdout: true) : sh(script: sonar_command, returnStdout: true)
            println "Executing: ${execute_sonar}"

            // Retrieve SonarQube Scan Results in JSON file
            withCredentials([usernamePassword(credentialsId: 'sonarqube_automation_user_password', passwordVariable: 'sonarqube_password', usernameVariable: 'sonarqube_username')]) {
                sh "curl -u '${sonarqube_username}:${sonarqube_password}' -X GET 'http://IP:9000/api/issues/search?componentkeys=${sonarProjectKey}' -o ${env.WORKSPACE}/sca_report.json"
            }

            println "[STATUS] Sonar Scan Execution Complete!"
        }
        catch(Exception e){
            currentBuild.result = 'FAILURE';
            println "[ERROR] An unexpected error occurred while executing ci_scanSonar.groovy as part of Sonar Scan stage: ${e.message}"
            error "[ERROR] CICD Pipeline Execution Failed!"
        }
    }
}
