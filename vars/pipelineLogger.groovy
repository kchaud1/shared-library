def printLog(String message, String messageLogLevel="INFO") {
    echo "[${messageLogLevel}] ${message}"
}

def info(String message) {
    printLog(message, "INFO")
}

def warn(String message) {
    printLog(message, "WARN")
}

def error(String message) {
    printLog(message, "ERROR")
}

def fatal(String message){
    printLog(message, "FATAL")
    error
}

def debug(String message) {
    if (env.logLevel != null && env.logLevel.toUpperCase() == "DEBUG"){
        printLog(message, "DEBUG")
    }
}

def setLogLevel(String logLevel) {
    env.logLevel=logLevel
    info("Set logLevel as ${env.logLevel}")
}

   /**
    * Centralized logger functionality for pipeline
    * By default, you can call pipelineLogger("message") and it will be printed to with logLevel INFO
    *
    * @param message - message to be printed to logs
    * @param messageLogLevel - Log Level of message, defaults to INFO
    * 
    */
def call(String message, String messageLogLevel="INFO") {
    switch(messageLogLevel) {
        case "INFO":
            info(message)
        break
        case "WARN":
            warn(message)
        break
        case "ERROR":
            error(message)
        break
        case "DEBUG":
            debug(message)
        break
        default:
            info(message)            
        break
    }
}
