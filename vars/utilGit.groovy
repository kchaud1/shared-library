#!/usr/bin/groovy
def GetLastCommitFiles() {
    commitHash = sh (script: "git log -n 1 --pretty=format:'%H'", returnStdout: true)
    echo "[INFO] Commit is $commitHash"
    echo "[INFO] Branch is '$env.BRANCH_NAME'"
   	filename = sh (script: "git show --name-only", returnStdout: true)
    echo "[INFO] change file name is $filename"
}

def getLastCommitID()
{ 
    echo "DEBUG: Get gitsha1/commmit_id from git repo"
    commit_id = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
    echo "DEBUG: got commit_id: $commit_id"
    return commit_id
}

def setEnvVarsFromGitProperties() {

    // git basic details, required for pipeline
    env.gitUrl = scm.getUserRemoteConfigs()[0].getUrl()-"https://"
    //env.gitProjectName = gitUrl.split("scm/")[1].split("/")[0].toLowerCase()
    
    env.gitRepoName = gitUrl.tokenize('/').last().split("\\.")[0]
	env.gitProjectName = "hdfc"
    env.gitBranchName = env.BRANCH_NAME.split("/").last()
    if (isUnix()){
        env.gitCommitHash = sh (script: "git log -n 1 --pretty=format:'%H'", returnStdout: true)
        env.gitCommitHashShort = sh (script: "git log -n 1 --pretty=format:'%h'", returnStdout: true)
    }else{
        env.gitCommitHash = bat(script: "git log -n 1 --pretty=format:'%H'", returnStdout: true)
        env.gitCommitHashShort = bat(script: "git log -n 1 --pretty=format:'%h'", returnStdout: true)
    }
}

def setAdvancedGitEnvProperties(){
    try {
        // Git commit details
        def gitCommitDate = sh (script: "git show --no-patch --no-notes --pretty='%cD' ${env.gitCommitHash} | cut -c1-25", returnStdout: true)
        def gitCommitterName = sh (script: "git show --no-patch --no-notes --pretty='%cn' ${env.gitCommitHash}", returnStdout: true)
        def gitCommitterSSO = sh (script: "git show --no-patch --no-notes --pretty='%cN' ${env.gitCommitHash}", returnStdout: true)
        def gitCommitterEmail = sh (script: "git show --no-patch --no-notes --pretty='%ce' ${env.gitCommitHash}", returnStdout: true)
        def gitCommitSubject = sh (script: "git show --no-patch --no-notes --pretty='%s' ${env.gitCommitHash}", returnStdout: true)

        env.gitCommitDate = gitCommitDate.trim()
        env.gitCommitterName = gitCommitterName.trim()
        env.gitCommitterSSO = gitCommitterSSO.trim()
        env.gitCommitterEmail = gitCommitterEmail.trim()
        env.gitCommitSubject = gitCommitSubject.trim()

        // Git URLs
        def gitRemoteUrl = scm.userRemoteConfigs[0].url 					  
        def gitUrlString = ("${gitRemoteUrl}" =~ /.*(?=\/)/)[0]
        def gitProjectUrl = "${gitUrlString}".replace("/scm/","/projects/")
        def gitBranchUrl = "${gitProjectUrl}/repos/${env.gitRepoName}/browse?at=${env.gitBranchName}"
        def gitCommitUrl = "${gitProjectUrl}/repos/${env.gitRepoName}/commits/${env.gitCommitHash}"

        env.gitProjectUrl=gitProjectUrl
        env.gitBranchUrl=gitBranchUrl
        env.gitCommitUrl=gitCommitUrl

        // Git history
        def gitDiffStat = sh (script: "git diff --stat ${env.gitCommitHash} ${env.gitCommitHash}~ | sort", returnStdout: true)
        def gitCommitGraph = sh (script: "git log --graph -5 --oneline", returnStdout: true)

        env.gitDiffStat = """$gitDiffStat"""
        env.gitCommitGraph = """$gitCommitGraph"""

        // debug logger
        pipelineLogger.debug("""
            Git variables:

                gitProjectName = $env.gitProjectName
                gitRepoName = $env.gitRepoName
                gitBranchName = $env.gitBranchName
                gitCommitHash = $env.gitCommitHash
                gitProjectUrl = $env.gitProjectUrl

        """) 
    }  catch(Exception e){
            pipelineLogger.fatal("Caught Exception, printing Stack Trace: ${e}")
    }
}

   /**
    * Wrapper function to get the value of 'gitTagging' variable from .env 
    * @param config - map of properties read in from .env
    * @return gitTagging - string containing the specified value   
    */
def getOptionalTagging(config) {
    def final TAGGING="gitTagging"
	def gitTagging = ""
    
    if (config.containsKey(TAGGING) && config[TAGGING] == "false" )
  	{
      return config[TAGGING]
    }
  	else
    {
      pipelineLogger.info("${gitTagging}")
      return true
    }
}

  /**
    * Check whether the tag already exists in git. If yes, remove the tag
    */
def removeGitTag(String tag){
    returnVal = sh script:"git rev-parse '${tag}' > /dev/null", returnStatus:true
    if ( returnVal == 0 ) {
        try {
            withCredentials([conjurSecretCredential(credentialsId: env.JENKINS_DAP_CREDS_RO, variable: 'CONJUR_SECRET')]){
                sh script:"git push https://${env.JENKINS_DAP_CREDS_RO}:${CONJUR_SECRET}@${env.gitUrl} --delete ${tag}", returnStatus:true
            }
        } catch(Exception e){
            pipelineLogger.error("Caught Exception, printing Stack Trace: ${e}")
        }
    }
}

   /**
    * Check whether the tag already exists in git.  If not, tag the current commit with the tag
    *   Supports the semVer tagging strategy
    * @param tag - map of properties read in from .env
    * @param message - if included, will create an annotated tag using the message
    * @param force - Not yet implemented, included here as a stub.  If set to 'true',
    *                       Will attempt to attach the tag to the current commit hash, even 
    *                       if the tag already exists in the repo.  May require deleting the 
    *                       tag before creating new tag. 
    * @param config - map of properties read in from .env
    */
def checkCreateGitTag(String tag, String message="", Boolean force=false,Map gitconfig=[:]) {
  def optional_tag = (gitconfig==null)? true : getOptionalTagging(gitconfig)
  
  // Checking gitTagging value in .env. If value is set to be false i.e (gitTagging=false) it will not create git gags.
  // For any other value other than false in gitTagging it will create new tags.
  try 
  {

    // updated function to use git with authenticate method. 
    //runGitWithAuth(env.JENKINS_DAP_CREDS_RO, env.JENKINS_DAP_CREDS_RO, "fetch https://${env.gitUrl} --tags --force")
    runGitWithAuth(kchaud1, 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, "fetch https://${env.gitUrl} --tags --force")
    returnVal = sh script:"git rev-parse '${tag}' > /dev/null", returnStatus:true
    if ( optional_tag == "false" )
    {
        pipelineLogger.info("Git Tag will not be created")
        return returnVal 
    }
    else
    {
        if ( returnVal != 0 || force )
        {
            //Tag does not exist in remote repo yet
            pipelineLogger.info("Tag '${tag}' does not yet exist in Bitbucket.  Attempting to create it...")
            String annotatedTagString=""
            if ( message != "" ) {
                annotatedTagString = "-a -m \"${message}\""
            } 
            String forceFlag=""
            if (force) {
                forceFlag="-f"
            }
            returnVal = sh script: "git tag ${forceFlag} ${annotatedTagString} ${tag} ", returnStatus:true
            assert returnVal == 0 : "Failed to create tag"
            runGitWithAuth(kchaud1, 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, "push https://${env.gitUrl} ${tag}")
            pipelineLogger("Successfully tagged commit ${env.gitCommitHash} as '${tag}'")
        }
        else {
            pipelineLogger.error("Found tag '${tag}' already existing in bitbucket.  To force creating the tag, include the argument 'force=true' in the method call.")
        }
    }
  }
  catch(Exception e){
      pipelineLogger("Error while creating or pushing tag '${tag}'", "ERROR")
      sh script:"git tag -d ${tag} > /dev/null 2>&1", returnStatus:true
      pipelineLogger.error("Caught Exception, printing Stack Trace: ${e}")
  }
}


// this function is replica of cloud-jenkins-shared-lib's ceGitUtil.groovy , please check if something needs to be updated at both location in case of bugs\enhancements.

// Runs a git command after setting up authentication for git using the given facelessId and secretRef. This function handles the Jenkins creds lookup and
// git ASKPASS mechanics.  It returns the return code of the git command.
// facelessId - the faceless account to use for authentication to git
// secretRef - a reference (ID) of a Jenkins credentials object that has the password for facelessId (must be a secret text or conjur secret credential type)
// gitCmd - the git command to be run (omit 'git' at start).  For example, to fetch tags gitCmd would be "fetch https://${env.gitUrl} --tags --force"
// Returns the return code
// Throws UnsupportedOperationException if not running on Linux (Windows is not supported)
// Throws IllegalArgumentException if the provided secretRef is of the wrong type (i.e. not a supported creds type)
def runGitWithAuth(String facelessId, String secretRef, String gitCmd) {

   // TODO: Uplift to support execution on Windows
   if (!isUnix()) {
      throw new UnsupportedOperationException("runGitWithAuth currently supports only Linux/UNIX agents. This method was called on a Jenkins agent where isUnix() is returning false.")
   }

   // TODO: Uplift to support returning output instead of rc (give to the caller as an option, e.g. with fourth param boolean returnStdOut, default=false)

   // Copy shell script needed for ASKPASS functionality and configure git to use it when running command
   if(!fileExists('com/hdfc/devsecops/askpass.sh')) {
      writeFile(file:"com/hdfc/devsecops/askpass.sh", text: libraryResource("com/hdfc/devsecops/askpass.sh"))
      sh(script: 'chmod 500 com/hdfc/devsecops/askpass.sh')
   }
   def command = "git -c core.askPass=com/hdfc/devsecops/askpass.sh " + gitCmd

   // Use withEnv and withCredentials to set the environment variables that the askpass.sh script needs
   def rc = 0
   withEnv(["GIT_USER=${facelessId}"]) {
      
      if(utilAuth.isConjurSecretCreds(secretRef)) {
         withCredentials([conjurSecretCredential(credentialsId: secretRef, variable: 'GIT_PW')]) {
            rc = sh(script: command)
         }
      }
      else if(utilAuth.isStringCreds(secretRef)) {
         withCredentials([string(credentialsId: secretRef, variable: 'GIT_PW')]) {
            rc = sh(script: command)
         }
      }
      else {
         throw new IllegalArgumentException("Jenkins secret reference '${secretRef}' is of the wrong type.  The secretRef argument must be the ID of a Jenkins credentials object of type Secret Text (String) or Conjur Secret")
      }
   }
   return rc
}
