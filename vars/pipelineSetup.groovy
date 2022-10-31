   /**
    * Parse any custom parameters for jenkins build out of the defined properties.
    * NOTE: These will be available on the NEXT build and will not be used this build.
    * Return a specially formatted List of parameters that Jenkins can consume
    *
    * @param config - map of properties read in from .env
    * @return paramList - list of parameters defined in .env
    */
def getPipelineParameters(Map config, Map additionalParams=[:]) {
    def final PARAMETER_LIST_STRING="customParameterList"
    def final PARAMETER_DEFAULT_VALUE_STRING="parameterDefaultValue_"
    def final PARAMETER_DESCRIPTION_STRING="parameterDescription_"

    //Note: 
    //Jenkins issue - 
    //Can't have parameterized build with no parameters or it will crash.  
    //There must be at least one parameter defined, but there is no requirement that it is used.
    
    //Note:
    //All parameters are added as build args to docker builds.  See stageDockerBuild.generateBuildArgsMap()



    def defaultParametersMap = [ 
        "UPSTREAM_IMAGE_TAG" : [
            "defaultValue" : "latest",
            "description" : "The docker image tag (version) for the upstream image"
        ],
        "UPSTREAM_IMAGE_BRANCH" : [
            "defaultValue" : "develop",
            "description" : "The branch name that the upstream docker image comes from"
        ]
    ]

    if ( env.pipelineType == "deploy" ) {
        defaultParametersMap.put(
            "INVOCATION_COMMAND", [
                "defaultValue" : "install",
                "description" : "The command passed to invocation image"
            ]
        )
        /*defaultParametersMap.put(
            "Last Successfully Deployed Tag", [
                "defaultValue" : "", 
                "description" : "The command passed to invocation image"
            ]
        )
        */

    }

    pipelineLogger.debug("Creating Parameters for build job.  Note if parameters have been modified (added/removed/default value changed), they will be available in the next build.")
    def paramNameList = []
    def paramList = []

    if (config.containsKey(PARAMETER_LIST_STRING)) {
        def paramString = config[PARAMETER_LIST_STRING]
        //paramNameList = paramString.replaceAll("[\"\\\\]","").replaceAll("[,]"," ").split()
        paramNameList = paramString.replaceAll("[\"\\\\]","").split(",")

    }

    //Read any custom parameters out of .env, if defined
    if (paramNameList.size() != 0) {
        pipelineLogger.debug("${PARAMETER_LIST_STRING} found in .env file, indicating custom parameters defined.  Attempting to parse the values")
        for (paramName in paramNameList){
            if (defaultParametersMap.containsKey(paramName)) {
                defaultParametersMap.remove(paramName)
            }

            def paramDefaultValue = config["${PARAMETER_DEFAULT_VALUE_STRING}${paramName}"]
            if (paramDefaultValue == null){
                paramDefaultValue = ""
            }
            def paramDescription = config["${PARAMETER_DESCRIPTION_STRING}${paramName}"]
            if (paramDescription == null){
                paramDescription = ""
            }
            pipelineLogger.debug("Found parameter '${paramName}' with default value '${paramDefaultValue}' and description '${paramDescription}'")
            def param = string(name: paramName, defaultValue: paramDefaultValue, description: paramDescription, trim: true)
            paramList.add(param)
        }
    }

    //Check whether any of the default parameters have been overridden
    for (paramName in defaultParametersMap.keySet()) {
        def paramDefaultValue = defaultParametersMap[paramName]["defaultValue"]
        def overrideDefaultValue=config["${PARAMETER_DEFAULT_VALUE_STRING}${paramName}"]
        if (overrideDefaultValue != null && overrideDefaultValue != "" ){
            pipelineLogger.debug("Found override for ${paramName} - default value set to ${overrideDefaultValue}")
            paramDefaultValue = overrideDefaultValue
        }
        def paramDescription = defaultParametersMap[paramName]["description"]
        def overrideDefaultDescription=config["${PARAMETER_DESCRIPTION_STRING}${paramName}"]
        if (overrideDefaultDescription != null && overrideDefaultDescription != ""){
            pipelineLogger.debug("Found override for ${paramDescription} - default value set to ${overrideDefaultDescription}")
            paramDescription = overrideDefaultDescription
        }  
        def param = string(name: paramName, defaultValue: paramDefaultValue, description: paramDescription, trim: true)
        paramList.add(param)
        pipelineLogger.debug("Adding parameter: name:'${paramName}', defaultValue:'${paramDefaultValue}', description:'${paramDescription}'")
    }

    for (paramName in additionalParams.keySet()){
        Map paramMap=additionalParams[paramName]
        String paramDefaultValue=paramMap["defaultValue"]
        String paramDescription=paramMap["description"]
        def param = string(name: paramName, defaultValue: paramDefaultValue, description: paramDescription, trim: true)
        paramList.add(param)
        pipelineLogger.debug("Adding parameter: name:'${paramName}', defaultValue:'${paramDefaultValue}', description:'${paramDescription}'")

    }
      /*  "UPSTREAM_IMAGE_TAG" : [
            "defaultValue" : "latest",
            "description" : "The docker image tag (version) for the upstream image"
        ],
    */

    assert paramList.size() > 0 : "FATAL: No pipeline parameters have been defined, including defaults.  This will cause pipeline to enter failed state.  Pipeline will terminate now."
    return paramList
}
   
 /**
    * Adding a function to fetch the production branch name from folder properties defined in jenkins configuration
    * This variable can be used globally in all scripts. Can only be defined in jenkins config of the repository as the plugin property.
    * Name of the plugin is folder properties.
    */
def setFolderProperties(Map config) 
{
	withFolderProperties  {
		if ( env.releaseBranch ) {
			config["releaseBranch"] = env.releaseBranch
        }
        else {
            if (config["releaseBranch"] == null ) {
                config.put("releaseBranch", "master")
            }
        }
    }
}
   /**
    * Read the properties file from repository into a map
    *
    * @param filePath - location of properties file, relative to root of repo.  Generally set to '.env'
    * @return config - map of properties read in from .env
    *    
    */
def getConfigMap(String filePath) {
    pipelineLogger("Reading properties file from '${filePath}'")
    def config = readProperties interpolate: true, file: filePath;
    return config
}
   /**
    * Check if pipelineLogLevel is defined in properties, use that custom value if defined.
    * Default level is info and will be used otherwise
    *    
    * @param config - map of properties read in from .env
    *
    */
def setPipelineLogLevel(Map config){

    def logLevel=null
    if ( config["logLevel"] != null && config["logLevel"] != "") {
        logLevel=config["logLevel"]
    }
    withFolderProperties  {
        if ( env.PIPELINE_LOG_LEVEL != null && env.PIPELINE_LOG_LEVEL != "") {
            logLevel=env.PIPELINE_LOG_LEVEL
        }
    }    
    println("setPipelineLogLevel(${logLevel})")

    pipelineLogger.setLogLevel(logLevel)
}
   /**
    * Calculate the type of pipeline that should be run.
    *  Options are "workflow" or "deploy" (compile is an optional step in "workflow")
    *  If pipelineType is defined as "deploy" in .env, that value will be used.  If not, 
    *  the value will be calculated based on the job name (which should be based on repo name) 
    *    
    * @param config - map of properties read in from .env
    */
def calculateAndSetPipelineTypeEnvVar(Map config) {
    def final PIPELINE_TYPES=["workflow", "compile", "deploy", "custom"]
    def final DEPLOY_TYPE_PIPELINE_REGEX = ~/.*((\/(deploy|config)-)|(-(deploy|config)\/)).*/
    // config-*
    // deploy-*
    // *-config
    // *-deploy
    def pipelineType = config["pipelineType"]
    if (pipelineType == null || pipelineType == ""){
        //pipelineType not set in .env, need to calculate from Job Name
        if (env.JOB_NAME ==~ DEPLOY_TYPE_PIPELINE_REGEX) {
            pipelineType="deploy"
        }
        else {
            pipelineType="compile"
        }
    }
    else {
        assert PIPELINE_TYPES.contains(pipelineType): "ERROR: Pipeline type: ${pipelineType} is not valid. Options are ${PIPELINE_TYPES}"
    }
    env.pipelineType=pipelineType
}

def setProductionBranchEnvVar(Map config) {

    def pipelineType=env.pipelineType
    def branchName=env.gitBranchName

    def isProduction=false

    if ( pipelineType == "compile" && config["releaseBranch"] == branchName ){
            isProduction=true
    }
    else if ( pipelineType == "deploy") {
        def PROD_DEPLOY_BRANCH_REGEX = ~/((e3|c3)-).*/
        if ( branchName ==~ PROD_DEPLOY_BRANCH_REGEX ){
            isProduction=true
        }
    }
    env.isProduction=isProduction
}

def findAndReadConfig(Map externalConfig = [:]) {
    def config = [:]
    def additionalConfig=[:]

    if (fileExists('CICD/.env')){
        config = getConfigMap('CICD/.env')
    }
    else if (fileExists('.env')) {
        config = getConfigMap('.env')
    }

    pipelineLogger.info("Checking for branch specific .env files")

    /*def foundFoldersArray=[]
    if (fileExists('CICD')){
        foundFoldersArray = sh(script: 'ls -1 CICD/', returnStdout: true).split()
        pipelineLogger.debug("foundFoldersArray=${foundFoldersArray}")
    }
    if ( foundFoldersArray.contains(env.gitBranchName)) {
        if ( fileExists("CICD/${env.gitBranchName}/.env")){
            pipelineLogger.info("Found .env at 'CICD/${env.gitBranchName}/.env'")
            additionalConfig=getConfigMap("CICD/${env.gitBranchName}/.env")
        }
        else {
            pipelineLogger.warn("Found folder 'CICD/${env.gitBranchName}/' but did not find .env file inside")
        }
    }
    else if ( foundFoldersArray.size() != 0 ) {
        def longestMatch=""
        
        for ( folderName in foundFoldersArray ){
            if ( folderName.startsWith("_") ){
                //remove first char (_) of folderName
                //folderName=folderName.drop(1)
                if ( env.gitBranchName.startsWith(folderName.drop(1))){
                    if (folderName.length() > longestMatch.length() && fileExists("CICD/${folderName}/.env")){
                        longestMatch=folderName
                    }
                }
            }
        }
        if ( longestMatch != "" ){
            pipelineLogger.info("Found .env at 'CICD/${longestMatch}/.env'")
            additionalConfig = getConfigMap("CICD/${longestMatch}/.env")
        }        
    }*/
    //Additional config (i.e. branch specific config) will overwrite any values set in general config
    config = config + additionalConfig + externalConfig
    pipelineLogger.debug("273------------Config=${config}")

    return config
}

/**
    * Read Custom Config 
    *
    * @param path - Directory path for system specific config file    
    * @return config - map of properties read in from config file
    *    
    */
def readCustomConfig(String path, String fileExtension) {     
     def config = [:]    
     
     String actualPath = path +  "/${env.gitBranchName}/" + fileExtension;
     if (fileExists(actualPath)) {
          pipelineLogger.info("Found system specific config at:" + actualPath)
          config = getConfigMap(actualPath)
     } else {
          pipelineLogger.info("Didn't found system specific config at:" + actualPath)
     }  

     return config;  
}


   /**
    * Run setup activities after checking out git repo so properties file is available
    *
    * @param filePath - location of properties file, relative to root of repo hfn jb.  Generally set to '.env'
    * @return config - map of properties read in from .env
    *    
    */
def call(boolean customParam = false, Map externalConfig = [:]) {
    pipelineLogger.info("Running Setup")
    utilGit.setEnvVarsFromGitProperties()
    def config = findAndReadConfig(externalConfig)
    //def globalVar = readYaml text: libraryResource('globalvars.yml')
  	setFolderProperties(config)
    //Setting up env based on LOB : future implementation
    //utilities.setLOBBaseEnv(config, globalVar)
   // def customPipelineScriptLocation = utilities.getCustomPipelineScriptLocation()
    //if ( customPipelineScriptLocation != "" ) {
      //  pipelineLogger.info("Custom pipeline script found at ${customPipelineScriptLocation}")
        //config.put("pipelineType","custom")
        //config.put("customPipelineScriptLocation",customPipelineScriptLocation)
    //}

    assert ! config.isEmpty():"ERROR: No .env file(s) or custom Pipeline scripts detected"

    setPipelineLogLevel(config)
    calculateAndSetPipelineTypeEnvVar(config)
    setProductionBranchEnvVar(config)
   
    //added condition for pipeline setup , by default true
    //String customRegistry=config["customRegistry"]
    String customRegistry = (config['customRegistry'] == null) ? "${customParam}":config['customRegistry']
    if (customRegistry == null || customRegistry.toBoolean() )
    {
         def pipelineParametersList = getPipelineParameters(config)
         utilities.setPipelineProperties(pipelineParametersList, config)
    }
    

    utilCalculateTag(config)
    /**
     * Print environment variables
     */
    String fullEnvVars = sh(script: "printenv | sort", returnStdout: true)
  	pipelineLogger.debug("343---------Full list of environment variables set: \n${fullEnvVars}")    
  
    return config
}
