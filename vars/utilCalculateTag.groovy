
def getRelease(config){
    def final RELEASE_STRING="release"
    def release = ""

    if (config.containsKey(RELEASE_STRING) && config[RELEASE_STRING] != ""){
        release = config[RELEASE_STRING]
    }
    else{
        pipelineLogger.error("You must set the variable 'release' in your .env file if using semVer or buildNumber versioning")
    }
    assert release != "" :"Error: 'release' is not defined in .env"
    return release
}

   
def calculateSemVerTag(config) {
    def final RELEASE_BRANCH_STRING="releaseBranch"
    def defaultReleaseBranch = "release"

    def tag = getRelease(config)    

    def releaseBranch = config[RELEASE_BRANCH_STRING]
    if ( releaseBranch == "") {
        releaseBranch=defaultReleaseBranch
    }

    if ( env.gitBranchName != releaseBranch ){
        tag ="${tag}.0-${env.gitBranchName}"
    }

    //need to write code to fetch git tag from the repository & if it fails then we have to break the pipeline
	
    
        return imageTag
     
      
}

def calculateBuildNumberTag(Map config) {
    def tag = getRelease(config)    

	imageTag = "${tag}-b${env.BUILD_NUMBER}-${env.BRANCH_NAME}"
    return imageTag    
}


def call(Map config) {
    def final PIPELINE_TYPE_STRING="pipelineType"
    def final TAGGING_STRATEGY_STRING="taggingStrategy"
    def final TAGGING_STRATEGY_COMPILE="semVer"
    def final TAGGING_STRATEGY_WORKFLOW="semVer"
    def final TAGGING_STRATEGY_DEPLOY="commitHash"
    def final INCLUDE_UPSTREAM_TAG_STRING="includeUpstreamTagInTag"
    def final TAGGING_STRATEGIES = ["semVer", "commitHash", "buildNumber"]

    def taggingStrategy = ""
    def includeUpstreamTag = ""

    //If user has defined tagging properties, use those instead
    def overrideTaggingStrategy = config[TAGGING_STRATEGY_STRING]
    if (overrideTaggingStrategy!= null && overrideTaggingStrategy != "" )
  {
        taggingStrategy = overrideTaggingStrategy
    	includeUpstreamTag=false
    }

    else{
        def pipelineType = env.pipelineType
        switch(pipelineType) {
            case "workflow":
                taggingStrategy = TAGGING_STRATEGY_WORKFLOW
                includeUpstreamTag = false
            case "compile":
                taggingStrategy = TAGGING_STRATEGY_COMPILE
                includeUpstreamTag = false
            break
            case "deploy":
                taggingStrategy = TAGGING_STRATEGY_DEPLOY
                includeUpstreamTag = true
            break
        }
    }
    
   
    assert TAGGING_STRATEGIES.contains(taggingStrategy) :"ERROR: Did not find tagging strategy '${taggingStrategy}' in ${TAGGING_STRATEGIES}"

    def tag = ""
    switch(taggingStrategy) {
        case "semVer":
            tag = calculateSemVerTag(config)
        break
        case "static":
            tag=getRelease(config)
        break
        case "commitHash":
            tag = env.gitCommitHash
        break
        case "buildNumber":
            tag = calculateBuildNumberTag(config)
        break
           
    }
    
    env.tag = tag
    config.put("tag", tag)
    pipelineLogger("Tag calculated to be ${env.tag}")

}
