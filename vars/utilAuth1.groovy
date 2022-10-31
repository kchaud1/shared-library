//Utility function that support federated authentication, so pipelines can use faceless IDs to authenticate to AWS.
import com.cloudbees.plugins.credentials.Credentials
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentials
import org.conjur.jenkins.conjursecrets.ConjurSecretUsernameCredentials
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials
import com.cloudbees.plugins.credentials.BaseCredentials
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials
import com.cloudbees.plugins.credentials.CredentialsDescriptor
import com.cloudbees.plugins.credentials.CredentialsScope
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor
import org.jenkinsci.plugins.credentialsbinding.MultiBinding
import org.jenkinsci.plugins.plaincredentials.StringCredentials

//Helper function that looks up a credentials reference in Jenkins and returns the credentials object.  Returns null if credsRef is null or if the credentials cannot be found.
def getCreds(String credsRef){
    if(credsRef == null) {
        pipelineLogger.warn("utilAuth.getCreds was invoked with a null argument (credentials reference).  Returning null")
        return null
    }
    def creds = null
    creds = CredentialsProvider.findCredentialById(credsRef, Credentials.class, currentBuild.getRawBuild(), null)
    if(creds == null) {
        pipelineLogger.warn("utilAuth.getCreds could not find the credentials referenced by ID ${credsRef}.  Returning null")
        return null
    }
    return creds
}

// Returns true if the Jenkins credentials object identified by credsRef are of type UsernamePassword (implements
// com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials), false otherwise
boolean isUsernamePasswordCreds(String credsRef) {
    def credsType = getCreds(credsRef)
    if(credsType instanceof UsernamePasswordCredentials) {
       return true
    }
    return false
}

// Returns true if the Jenkins credentials object identified by credsRef are of type ConjurSecretUsername (implements
// org.conjur.jenkins.conjursecrets.ConjurSecretUsernameCredentials), false otherwise
boolean isConjurSecretUsernameCreds(String credsRef) { 
    def credsType = getCreds(credsRef)
    if(credsType instanceof ConjurSecretUsernameCredentials) {
        return true
    }
    return false
}

// Returns true if the Jenkins credentials object identified by credsRef are of type ConjurSecret (implements
// org.conjur.jenkins.conjursecrets.ConjurSecretCredentials), false otherwise
boolean isConjurSecretCreds(String credsRef) {
    def credsType = getCreds(credsRef)
    if(credsType instanceof ConjurSecretCredentials) {
        return true
    }
    return false
}

// Returns true if the Jenkins credentials object identified by credsRef are of type AmazonWebServices (implements
// com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials), false otherwise
boolean isAWSCreds(String credsRef) {
    def credsType = getCreds(credsRef)
    if(credsType instanceof AmazonWebServicesCredentials) {
        return true
    }
    return false
}

// Returns true if the Jenkins credentials object identified by credsRef are of type StringCredentials (used for Jenkins
// secret text credentials), false otherwise
boolean isStringCreds(String credsRef) {
    def credsType = getCreds(credsRef)
    if(credsType instanceof StringCredentials) {
        return true
    }
    return false
}


// Checks if the current caller is a member of (at least) one of the given ActiveDirectory groups. Generally this function is useful for manually triggered jobs
// were you want a more fine-grained access control check that is attainable with Jenkins roles.  An AccessControlException is thrown if the caller
// is not a member of any of the given groups.  The function returns successfully otherwise.  The check is performed using the BUILD_USER_GROUPS environment
// var which is set by the BuildUser plugin.  If this var is not set or empty, access is denied and an Exception is thrown. The group list
// may be provided in upper/lower/mixed case--internally this function will normalize to lowercase group names.
void checkGroupAuth(List<String> groups) {

    // Normalize the given list and the caller's groups to lowercase for future comparison
    def authorizedGroups = []
    if(groups == null || groups.size()==0) {
        throw new Exception("Job Access Denied. No groups were given that are allowed to run this job (given list of authorized groups was null or zero-length)")
    }
    else {
        for(g in groups) {
            authorizedGroups.add(g.toLowerCase())
        }
    }
    def caller = "Unknown"
    def callersGroups = []
    wrap([$class: 'BuildUser']) {
        if(env.BUILD_USER_ID != null && !"".equals(env.BUILD_USER_ID)) {
            caller = env.BUILD_USER_ID
        }
        if(env.BUILD_USER_GROUPS != null && !"".equals(env.BUILD_USER_GROUPS)) {
            callersGroups = env.BUILD_USER_GROUPS.toLowerCase().split(',')
        }
    }
    for(g in authorizedGroups) {
        if(callersGroups.contains(g)) {
            pipelineLogger.info("utilAuth.checkGroupAuth: Authorization check passed.  Caller ${caller} is a member of group ${g}")
            return
        }
    }
    throw new Exception("Job access denied. Caller ${caller} is not a member of any group authorized to run this pipeline (allowed groups are ${authorizedGroups}).")
}

// Enables finer-grained access control within a pipeline by checking if the caller is a member of (at least) one of the AD groups allowed to run a certain operation. Generally useful
// for pipelines that are not triggered by source control changes (e.g. pipeline jobs manually run through the Jenkins UI, or via API call).
// Authorizations are specified in a YAML file having the following format (by default, call this file JenkinsAuth.yaml and place at repo root).
// ---
// runJob:                  # operation name -- must be unique within YAML file and case-sensitive
//   authorizedGroups:      # list of groups allowed to execute 'runJob' operation
//     - AD-Group-1         # Active directory group name (case insensitive)
//     - AD-Group-2
//
// The authorizations file is located/loaded using these parameters:
//   authFile   - Name of the YAML file containiner authorizations.  The default is "JenkinsAuth.yaml" and assumed to be at the repo root
//   repoURL    - Optional URL of the git repo from which to load authFile.  Default is null.  If not given (or null), authFile is loaded from the current workspace.
//                Note, this requires that this function be invoked following the pipeline's 'checkout scm' step.
//   repoBranch - Branch name to checkout of repoURL. required if repoURL is specified, ignored otherwise.
//
// Operation names are arbitrary and at the caller's discretion.  Simple pipelines where the authorization is binary (e.g. can run or can't) will likely use a single
// operation such as 'runJob' in the JenkinsAuth.yaml file (as shown above), and the pipeline code will look like
//    checkout scm
//    checkOpAuth("runPipeline")
//    ... remaining pipeline steps...
// More complex pipelines may define multiple operations and check different authorizations based on parameters and/or user input, job flow, etc.
//
// If authorization is successful, this function returns normally.  An Exception is thrown if
// - The authorization file cannot be found/loaded
// - The given operation is not found in the authorization file (i.e. implicit deny)
// - The caller is not a not a member of any of the groups authorized to perform operation
// - The caller's information cannot be determined from the BUILD_USER_GROUPS environment var (e.g. var is not set or empty).
//
// IMPORTANT NOTES:
// - If a repoUrl and repoBranch are given, this function will perform a git checkout.  If run in a pipeline that has checked out other code, you will need to re-run that checkout
//   after invoking this function (this function's checkout will replace your other checkout in the Jenkins agent workspace).  Generally this is not an issue because you will check
//   authorization early in the pipeline (i.e. before checking out other code). However, if you do something like
//       checkout scm
//       ceAuthUtil.checkOpAuth('myOp', 'JenkinsAuth.yaml', 'https://develop.instant.com/myRepo.git', 'develop')        <--overwrites what was checked out above
//   you will need another 'checkout scm'.  The easy way to avoid this is to call checkOpAuth first or, if possibly, put the authorization file in your app's source repo and instead do
//       checkout scm
//       ceAuthUtil.checkOpAuth('myOp')
void checkOpAuth(String operation, String authFile="JenkinsAuth.yaml", String repoUrl=null, String repoBranch=null) {
    def authorizations = [:]
    def gitCredentials =  env.BITBUCKET_CREDS_RO
    if(repoUrl != null) {
        if(repoBranch == null) {
            throw new Exception("Missing required argument. A repoUrl agument was given (${repoUrl}), but no branch was specified.  The repoBranch arg is required when repoUrl is specified.")
        }
        else {
            try {
                git credentialsId: gitCredentials, url: repoUrl, branch: repoBranch
                authorizations = readYaml file: authFile
            }
            catch(Exception e) {
                throw new Exception("Job access denied because authorization file ${authFile} could not be found or loaded successfully from the ${repoBranch} branch of git repo '${repoUrl}'. ${e.getMessage()}")
            }
        }
    }
    try {
        authorizations = readYaml file: authFile
    }
    catch (Exception e) {
        throw new Exception("Job access denied because authorization file ${authFile} could not be found or loaded successfully from the Jenkins workspace. Please ensure source code is checked out prior to calling this function, and ${authFile} exists in the source code repository. ${e.getMessage()}")
    }

    if(authorizations[operation] == null || authorizations[operation]['authorizedGroups'] == null ||
       !authorizations[operation]['authorizedGroups'] instanceof List || authorizations[operation]['authorizedGroups'].size() == 0) {
        throw new Exception("Job access denied because authorization file ${authFile} does not define operation ${operation} or no authorizedGroups list was provided for that operation.")
    }
    checkGroupAuth(authorizations[operation]['authorizedGroups'])
    pipelineLogger.info("ceAuthUtil.checkOpAuth: Authorization check passed for operation ${operation}")
}
