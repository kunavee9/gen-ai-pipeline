
def call(){
    try{
        println "[STATUS] Initiating Quality Gate check process!"

        def configFile = readJSON file: "${env.WORKSPACE}/pipelineconfig.json"
        def appname = configFile."appname"
        def sonarProjectKey = configFile."qualitygates"."sonarProjectKey", sonarEnforcement = configFile."qualitygates"."sonarEnforcement"
        def trivyThreshold = configFile."qualitygates"."trivyThreshold", trivyEnforcement = configFile."qualitygates"."trivyEnforcement"

        // Sonar Quality Gate
        withCredentials([usernamePassword(credentialsId: 'sonarqube_automation_user_password', passwordVariable: 'sonarqube_password', usernameVariable: 'sonarqube_username')]){
            def sonar_qualitygate_command = "curl -u ${sonarqube_username}:${sonarqube_password} http://44.217.100.193:9000/api/qualitygates/project_status?projectKey=${sonarProjectKey}"

            def execute_sonar_qualitygate = (env.agent_os == "windows") ? bat(script: sonar_qualitygate_command, returnStdout: true).toString().trim() : sh(script: sonar_qualitygate_command, returnStdout: true).toString().trim()

            def sonar_api_response = readJSON text: execute_sonar_qualitygate

            def sonar_qualitygate = sonar_api_response."projectStatus"."status"

            if(sonar_qualitygate == "OK"){
                echo "[STATUS] Sonar Quality Gate Passed!"
                env.SCA_QUALITY_GATE = "SUCCESS"
            }
            else{
                if(sonarEnforcement == true){
                    echo "[ERROR] Sonar Quality Gate Failed with Response: ${sonar_qualitygate}; Force Failing CICD Pipeline since Sonar Enforcement is enabled!"
                    env.SCA_QUALITY_GATE = "FAILED"
                }
                else{
                    echo "[WARNING] Sonar Quality Gate Failed with Response: ${sonar_qualitygate}; Bypassing Quality Gate since Sonar Enforcement is disabled!"
                    env.SCA_QUALITY_GATE = "BYPASSED"
                }
            }
        }

        // Trivy Quality Gate
        def trivy_results = readFile file: "${env.WORKSPACE}/sast_report.json"

        if(trivy_results.contains("\"Severity\": \"${trivyThreshold.toUpperCase()}\"")){
            if(trivyEnforcement == true){
                env.SAST_QUALITY_GATE = "FAILED"
                echo "[ERROR] Trivy Quality Gate Failed; Force Failing CICD Pipeline since Trivy Enforcement is enabled!"
            }
            else{
                echo "[WARNING] Trivy Quality Gate Failed; Bypassing Quality Gate since Trivy Enforcement is disabled!"
                env.SAST_QUALITY_GATE = "BYPASSED"
            }
        }
        else{
            echo "[STATUS] Trivy Quality Gate Passed!"
            env.SAST_QUALITY_GATE = "SUCCESS"
        }

        echo "Predicting Quality Gate Status based on SCA, SAST Quality Gate Results and CI RAG Prediction model..."

        // Set Quality Gate Status based on Trained model and SCA/SAST Quality Gates
        def predict_quality_gate_status = libraryResource('PythonAutomation/GenAI/MLModel/predict_ci_rag.py')
        writeFile file: 'predict_ci_rag.py', text: predict_quality_gate_status

        def ci_rag_status = sh(script: "python3 predict_ci_rag.py --SCA_STATUS ${env.SCA_QUALITY_GATE} --SAST_STATUS ${env.SAST_QUALITY_GATE}", returnStdout: true).trim()
        
        ansiColor('xterm') {
            sh "python3 predict_ci_rag.py --SCA_STATUS ${env.SCA_QUALITY_GATE} --SAST_STATUS ${env.SAST_QUALITY_GATE}"
        }

        def final_status = ci_rag_status.split(":")[1].trim()
        final_status = (final_status == "RED") ? "FAILED" : (final_status == "GREEN") ? "SUCCESS" : "BYPASSED"
        
        env.FINAL_QUALITY_GATE_STATUS = final_status
        
        if(final_status == "SUCCESS"){
            echo "[STATUS] Quality Gate Check Passed!"
        }
        else if(final_status == "FAILED"){
            echo "[ERROR] Quality Gate Check Failed; Force Failing CICD Pipeline!"
            currentBuild.result = 'FAILURE';
            error "[ERROR] CICD Pipeline Execution Failed!"
        }
        else{
            echo "[WARNING] Quality Gate Check Bypassed!"
        }
    }
    catch(Exception e){
        currentBuild.result = 'FAILURE';
        println "[ERROR] An unexpected error occurred while executing ci_qualityGate.groovy as part of Quality Gate stage: ${e.message}"
        error "[ERROR] CICD Pipeline Execution Failed!"
    }
}
