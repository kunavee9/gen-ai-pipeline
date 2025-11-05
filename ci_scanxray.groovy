def call(){
    def configFile = readJSON file: "${env.WORKSPACE}/pipelineConfig.json"
    def applicationName = configFile."applicationName", applicationType = configFile."applicationType", container = configFile."build"."container", artifactType = configFile."build"."artifactType"
    def serverId = configFile."jfrogServerId", watches = configFile."watches"
    def xrayCmd
    try{
        ansiColor('xterm'){
            echo "\033[0;32m[STATUS] Xray Scan In-Progress for Application: ${applicationName}"
        }
        // Xray Scan execution based on Application Type using JFROG CLI
        if(container == true){
            unstash 'DockerImage'
            sh "docker load -i ${env.IMAGE_TAG}.tar"
            xrayCmd = "${env.LINUX_CLI_JFROG}/jf docker scan ${env.FULL_IMAGE_AND_TAG} --watches '${watches}' --server-id ${serverId} --format=json > xray.json"
        }
        else{
            if(applicationType == "ReactJS" || applicationType == "VueJS" || applicationType == "Angular"){
                xrayCmd = "${env.LINUX_CLI_JFROG}/jf scan ${env.WORKSPACE}/${applicationName}-${applicationType}/* --watches '${watches}' --server-id ${serverId} --format=json > xray.json"
            }
            else if(applicationType == "Springboot"){
                xrayCmd = "${env.LINUX_CLI_JFROG}/jf scan ${env.WORKSPACE}/${env.artifactSourcePath}-${env.packageVersion}.${artifactType} --watches '${watches}' --server-id ${serverId} --format=json > xray.json"
            }
            else{
                error "[ERROR] Value in pipelineConfig.json file for 'applicationType' not defined; Expected values: Springboot/ReactJS/NodeJS/VueJS/Angular/Scala, Received: ${applicationType}"
            }
        }
        echo "${xrayCmd}"
        sh label: "Xray Scan", script: xrayCmd
        def logFile = readJSON file: "${env.WORKSPACE}/xray.json"
        // Get Threshold from enforcement file, if not available set default threshold
        def xrayThresholdlevel
        if(configFile.containsKey("xray-threshold") && configFile."xray-threshold" != ""){
            xrayThresholdlevel = configFile."xray-threshold"
        }
        else{
            xrayThresholdlevel = "Critical"
        }
        
        echo "Xray Severity Threshold Level (LOW/MEDIUM/HIGH/CRITICAL): ${xrayThresholdlevel.toUpperCase()}"
        // Generate XML file from JSON log to create a Sortable Summary Report Table
        def writer = new StringWriter()
        writer = '<table sorttable="yes"><tr><td value="IssueID" /><td value="Severity" /><td value="FixedVersions" /><td value="Summary" /><td value="Component" /></tr>'
     if(logFile.size()>0 && logFile.get(0).containsKey("violations")){
            logFile.get(0).violations.each{v->
                def fixedVersion
                def issue = v["issue_id"]
                def sev = v["severity"]
                def condition 
                if (xrayThresholdlevel == "Low"){
                    condition = sev =="Low" || sev == "Medium" || sev == "High" || sev == "Critical"
                }
                else if (xrayThresholdlevel == "Medium"){
                    condition = sev == "Medium" || sev == "High" || sev == "Critical"
                }
                else if (xrayThresholdlevel == "High"){
                    condition = sev == "High" || sev == "Critical"
                }
                else if (xrayThresholdlevel == "Critical"){
                    condition = sev == "Critical"
                }
            
                if(condition){
                    env.XRAY_QUALITY_GATE_STATUS = "FAILED"
                    def summ = v["summary"]
                    summ = summ.replaceAll('<','&lt;')
                    def comp =  v["components"].keySet()
                    comp.each {entry ->
                        fixedVersion = v["components"][entry]["fixed_versions"]
                    }
                    if(summ==null){
                        summ='NA'
                    }
                    else{
                        summ=summ.replaceAll('"','')
                    }
                    writer= writer + '<tr><td value="' + "${issue}" +'"/><td value="' + "${sev}" +'"/><td value="' + "${fixedVersion}" +'"/><td value="' + "${summ}" +'"/><td value="' + "${comp}" +'"/></tr>'
                }
            }
        }
    if(env.XRAY_QUALITY_GATE_STATUS != "FAILED"){
            env.XRAY_QUALITY_GATE_STATUS = "SUCCESS"
            writer = '<table sorttable="yes"><tr><td value="Xray Scan Result" /></tr><tr><td value="No Violation Found" /></tr>'
        }
        writer= writer + '</table>'
        writeFile file : 'xrayResults.xml', text: "${writer}"
        // Plugin to display Summary Report in Table format using XML file
        archiveArtifacts artifacts : 'xrayResults.xml',allowEmptyArchive: true,fingerprint : true, onlyIfSuccessful: true
        step([$class: 'ACIPluginPublisher', name : 'xrayResults.xml', shownOnProjectPage : true])
    }
    catch(Exception e){
        currentBuild.result = 'FAILURE';
        ansiColor('xterm'){
            echo "\033[0;31m[ERROR] An Unexpected error occurred while executing CICD process: ${e.message}"
        }
        error "[ERROR] CICD Pipeline Failed during Xray Scan Execution process: ci_scanXray.groovy"
    }
}

