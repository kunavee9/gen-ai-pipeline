def call(){
    def configFile = readJSON file: "${env.WORKSPACE}/pipelineConfig.json"
    def applicationName = configFile."applicationName", container = configFile."build"."container", checkmarxGroup = configFile."build"."checkmarxGroup"
    def checkmarxCmd
    try{
        ansiColor('xterm'){
            echo "\033[0;32m[STATUS] Checkmarx Scan In-Progress for Application: ${applicationName}"
        }
        // Execute Checkmarx for applications based on checkmarxGroup flag in pipelineConfig.json file
        if(configFile."build".containsKey("checkmarxGroup") && configFile."build"."checkmarxGroup" != ""){
            // Plugin for Checkmarx scan execution
            checkmarxASTScanner additionalOptions: '--sast-incremental --project-groups "'+"${checkmarxGroup}"+'"', branchName: "${env.GIT_BRANCH}", checkmarxInstallation: 'checkmarx', credentialsId: 'checkmarx', projectName: "${applicationName}", useOwnAdditionalOptions: true
            // Retrieve Checkmarx Report in JSON format
            def curlOp = sh(script: "curl -s ${env.BUILD_URL}artifact/", returnStdout: true).trim().toString()
            def capturedString
            (curlOp =~ /(cx\.tmp\d+)/).with {matcher ->
                if (matcher.find()) {
                    capturedString = matcher.group(1)
                }
                else{
                    error "[ERROR] Matcher not found; Failed during Checkmarx JSON Log generation process"
                }
            }
            sh "curl -s -O ${env.BUILD_URL}artifact/${capturedString}/checkmarx-ast-results.json"   // Save JSON result in Jenkins Workspace
            // DQT-2406 Capability to enable Checkmarx Quality Gate Implementation
            def logFile = readJSON file: "${env.WORKSPACE}/checkmarx-ast-results.json"
            // Get Threshold from pipelineConfig file, if not available set default threshold
            def checkmarxThresholdlevel
            if(configFile.containsKey("checkmarx-threshold") && configFile."checkmarx-threshold" != ""){
                checkmarxThresholdlevel = configFile."checkmarx-threshold"
            }
            else{
                checkmarxThresholdlevel = "Critical"
            }
            echo "Checkmarx Severity Threshold Level (LOW/MEDIUM/HIGH/CRITICAL): ${checkmarxThresholdlevel.toUpperCase()}"
            def severityOrder = ["Critical", "High", "Medium", "Low"]       // Severity Order for Quality Gates
            def enginesToCheck = ["sast"]      // Categories to be validated for Quality Gates
            def enginesResult = logFile."EnginesResult"
            
            def thresholdIndex = severityOrder.indexOf(checkmarxThresholdlevel)
            def applicableSeverities = severityOrder[0..thresholdIndex]
            def hasFindings = enginesToCheck.any { engine ->
                def engineData = enginesResult[engine]
                if (!engineData) return false
                return applicableSeverities.any { severity ->
                    def value = engineData[severity]
                    return value && value > 0
                }
            }
            env.CHECKMARX_QUALITY_GATE_STATUS = hasFindings ? "FAILED" : "SUCCESS"
            
        }
        else{
            ansiColor('xterm'){
                error "\033[0;31m[ERROR] Application not enabled for Checkmarx Scan execution; To enable Checkmarx Scan add 'checkmarxGroup' parameter and its corresponding value with in pipelineConfig.json file"
            }
        }
    }
    catch(Exception e){
        currentBuild.result = 'FAILURE';
        ansiColor('xterm'){
            echo "\033[0;31m[ERROR] An Unexpected error occurred while executing CICD process: ${e.message}"
        }
        error "[ERROR] CICD Pipeline Failed during Checmarx Scan Execution process: ci_scanCheckmarx.groovy"
    }
}

