   /**
    * Wrapper function to get the value of 'release' variable from .env 
    * @param config - map of properties read in from .env
    * @return release - string containing the specified value   
    */
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

   /**
    * Calculate the tag using automatic semantic versioning strategy.  Tag will depend on the branch-
    *
    *   If build is happening against the branch that is released to production (could be either prod or release depending on branching strategy):
    *   The tag will evaluate to something like '1.2.0', where
    *       -release=1.2 in the .env, and 
            -There have been zero commits on this branch since it was incremented to 1.2 (that is, THIS commit incremented the version)
    *   If the next commit leaves the same 'release=1.2', it is assumed this is a bugfix, and will increment the tag to 1.2.1
    *   To track of this, Jenkins will add a tag into Bitbucket each time it detects that release is incremented.  When you increment
    *   release=1.3, the tag "1.3" will be applied to that commit automatically
    *
    *   If the build is happening on a branch that does not deploy to production (i.e. develop):
    *   The tag will evaluate to '1.2.0-develop.0', where:
    *       -release=1.2 in .env file
    *       -current branch is develop
    *        -There have been zero commits on this branch since it was incremented to 1.2 (that is, THIS commit incremented the version)
    *    On the next commit, assuming the release is not incremented again, the tag would be set to '1.2.0-develop.1'
    *    This indicates that the current work is being done towards a release tagged "1.2.0" which would be build from the production branch
    *
    *   @param config - map of properties read in from .env
    *   @return imageTag - string in the form '1.2.3' or 1.2.0-develop.2   
    */
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

    utilGit.checkCreateGitTag(tag,"",false,config)
    def optional_tag = utilGit.getOptionalTagging(config)
    
    // Adding try/catch condition to check if value of count is found i.e if git tag is found in repo it will return the imagetag. If not it will check if gitTagging=false then it will set count as 0 and return imageTag.
  	try {
      if ( optional_tag == "false" ) {
        //pipelineLogger.info("Git tag validated as false in calculate")
        def count = sh (script:"git rev-list ${env.gitCommitHash} --count", returnStdout: true).replaceAll("\\s","")
        def imageTag = "${tag}.${count}"
        return imageTag
	  	}
      else
      {
        pipelineLogger.info("tag - ${tag} env.gitCommitHash -  ${env.gitCommitHash} ")
        def count = sh (script:"git rev-list '${tag}'..${env.gitCommitHash} --count", returnStdout: true).replaceAll("\\s","")
        def imageTag = "${tag}.${count}"
        //pipelineLogger.info("${imageTag}")
        return imageTag
      }
    }
    catch (e)
    {
		pipelineLogger("Error while creating tag", "ERROR")
        pipelineLogger.error("Caught Exception, printing Stack Trace: ${e}")
    }
      
}

   /**
    * Calculate the tag using build number strategy, i.e. '1.2.3-b20' 
    *   Where release=1.2.3 in .env
    *   And current build number is 20.
    * @param config - map of properties read in from .env
    * @return imageTag - string in the form '1.2.3-b20'   
    */
def calculateBuildNumberTag(Map config) {
    def tag = getRelease(config)    

    imageTag = "${tag}-b${env.BUILD_NUMBER}"
    return imageTag    
}


   /**
    * Calculate the tag for the Docker image based on the tagging strategy
    * Depending on tagging strategy, may also push tag to Bitbucket
    *
    *   Current (8/13/19) tagging strategies include:
    *    -semVer (see calculateSemVerTag() )
    *    -buildNumber (see calculateBuildNumberTag)
    *    -commitHash (commit hash of checkout that is currently building)
    *    -latestOnly (will only push 'latest' tag, no hard tag - this will be overwritten each time and should only be used for dev environments)
    *    
    *    By default each tag will be pushed along with the "latest" tag, with the exception of 'latestOnly'.
    * @param config - map of properties read in from .env
    *    
    */
def call(Map config) {
    def final PIPELINE_TYPE_STRING="pipelineType"
    def final TAGGING_STRATEGY_STRING="taggingStrategy"
    def final TAGGING_STRATEGY_COMPILE="semVer"
    def final TAGGING_STRATEGY_WORKFLOW="semVer"
    def final TAGGING_STRATEGY_DEPLOY="commitHash"
    def final INCLUDE_UPSTREAM_TAG_STRING="includeUpstreamTagInTag"
    def final TAGGING_STRATEGIES = ["semVer", "commitHash", "buildNumber", "static","dateTimeStamp"]

    def taggingStrategy = ""
    def includeUpstreamTag = ""

    //If user has defined tagging properties, use those instead
    def overrideTaggingStrategy = config[TAGGING_STRATEGY_STRING]
    if (overrideTaggingStrategy!= null && overrideTaggingStrategy != "" )
  {
        taggingStrategy = overrideTaggingStrategy
    	includeUpstreamTag=false
    // Hot Fix 662022 : due to default null values being updated at else statement the LAtest tag started to poped up in image, Commenting
    // below conditions as to keep includeUpstreamTag as false in case overrideTaggingStrategy is defined at .env 
    // this is make sure to have everything work till user override. if you are looking to make it true , please use switch case below per condition. 
        //if ( taggingStrategy == "semVer") {
        //    includeUpstreamTag=false
        //}
        //else {
         //   includeUpstreamTag=true
       // }
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

    
    


    def overrideIncludeUpstreamTag = config[INCLUDE_UPSTREAM_TAG_STRING]
    //pipelineLogger.debug("overrideIncludeUpstreamTag='${overrideIncludeUpstreamTag}'")
    if ( overrideIncludeUpstreamTag != null ) {
        if ( overrideIncludeUpstreamTag == "true" ) {
            includeUpstreamTag = true
        }
        else if ( overrideIncludeUpstreamTag == "false" ) {
            includeUpstreamTag = false
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
        case "latestOnly":
            tag = ""
            includeUpstreamTag=false
        break
        case "dateTimeStamp":
            Date date = new Date()
            tag=getRelease(config)+"-v" + date.format("MMddyyyy") 
        break            
    }
    assert (tag != "" || taggingStrategy == "latestOnly") : "ERROR: tag has been calculated as ''"

    if (includeUpstreamTag == true) {
        tag = "${params.UPSTREAM_IMAGE_TAG}-${tag}"
    }
    env.tag = tag
    config.put("tag", tag)
    pipelineLogger("Tag calculated to be ${env.tag}")

}
