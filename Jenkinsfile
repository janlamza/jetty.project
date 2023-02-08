#!groovy

pipeline {
  agent none
  // save some io during the build
  options {
    skipDefaultCheckout()
    durabilityHint('PERFORMANCE_OPTIMIZED')
    buildDiscarder logRotator( numToKeepStr: '60' )
  }
  stages {
    stage("Parallel Stage") {
      parallel {
        stage("Build / Test - JDK19") {
          agent { node { label 'linux' } }
          steps {
            timeout( time: 180, unit: 'MINUTES' ) {
              checkout scm
              mavenBuild( "jdk19", "clean install -Dspotbugs.skip=true -Djacoco.skip=true", "maven-3.9.x")
              recordIssues id: "jdk19", name: "Static Analysis jdk19", aggregatingResults: true, enabledForFailure: true, tools: [mavenConsole(), java(), checkStyle()]
            }
          }
        }

        stage("Build / Test - JDK17") {
          agent { node { label 'linux' } }
          steps {
            timeout( time: 180, unit: 'MINUTES' ) {
              checkout scm
              mavenBuild( "jdk17", "clean install javadoc:javadoc -Perrorprone", "maven-3.9.x")
              // Collect up the jacoco execution results (only on main build)
              jacoco inclusionPattern: '**/org/eclipse/jetty/**/*.class',
                     exclusionPattern: '' +
                             // build tools
                             '**/org/eclipse/jetty/ant/**' +
                             ',*/org/eclipse/jetty/maven/its/**' +
                             ',**/org/eclipse/jetty/its/**' +
                             // example code / documentation
                             ',**/org/eclipse/jetty/embedded/**' +
                             ',**/org/eclipse/jetty/asyncrest/**' +
                             ',**/org/eclipse/jetty/demo/**' +
                             // special environments / late integrations
                             ',**/org/eclipse/jetty/gcloud/**' +
                             ',**/org/eclipse/jetty/infinispan/**' +
                             ',**/org/eclipse/jetty/osgi/**' +
                             ',**/org/eclipse/jetty/http/spi/**' +
                             // test classes
                             ',**/org/eclipse/jetty/tests/**' +
                             ',**/org/eclipse/jetty/test/**',
                     execPattern: '**/target/jacoco.exec',
                     classPattern: '**/target/classes',
                     sourcePattern: '**/src/main/java'
              recordIssues id: "jdk17", name: "Static Analysis jdk17", aggregatingResults: true, enabledForFailure: true, tools: [mavenConsole(), java(), checkStyle(), errorProne(), spotBugs()]
            }
          }
        }
      }
    }
  }
  post {
    failure {
      slackNotif()
    }
    unstable {
      slackNotif()
    }
    fixed {
      slackNotif()
    }
  }
}

def slackNotif() {
  script {
    try {
      if ( env.BRANCH_NAME == 'jetty-10.0.x' || env.BRANCH_NAME == 'jetty-11.0.x' || env.BRANCH_NAME == 'jetty-12.0.x' ) {
        //BUILD_USER = currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
        // by ${BUILD_USER}
        COLOR_MAP = ['SUCCESS': 'good', 'FAILURE': 'danger', 'UNSTABLE': 'danger', 'ABORTED': 'danger']
        slackSend channel: '#jenkins',
                  color: COLOR_MAP[currentBuild.currentResult],
                  message: "*${currentBuild.currentResult}:* Job ${env.JOB_NAME} build ${env.BUILD_NUMBER} - ${env.BUILD_URL}"
      }
    } catch (Exception e) {
      e.printStackTrace()
      echo "skip failure slack notification: " + e.getMessage()
    }
  }
}

/**
 * To other developers, if you are using this method above, please use the following syntax.
 *
 * mavenBuild("<jdk>", "<profiles> <goals> <plugins> <properties>"
 *
 * @param jdk the jdk tool name (in jenkins) to use for this build
 * @param cmdline the command line in "<profiles> <goals> <properties>"`format.
 * @return the Jenkinsfile step representing a maven build
 */
def mavenBuild(jdk, cmdline, mvnName) {
  script {
    try {
      withEnv(["JAVA_HOME=${ tool "$jdk" }",
               "PATH+MAVEN=${ tool "$jdk" }/bin:${tool "$mvnName"}/bin",
               "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
        configFileProvider(
                [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS'), configFile(fileId: 'maven-build-cache-config.xml', variable: 'MVN_BUILD_CACHE_CONFIG')]) {
          sh "ls -alrt"
          sh "cp $MVN_BUILD_CACHE_CONFIG ./mvn/"
          sh "mvn -Dmaven.repo.uri=http://nexus-service.nexus.svc.cluster.local:8081/repository/maven-public/ -Dmaven.test.failure.ignore=true -ntp -s $GLOBAL_MVN_SETTINGS -Dmaven.repo.local=.repository -Pci -V -B -e $cmdline"
        }
      }
    }
    finally
    {
      junit testResults: '**/target/surefire-reports/*.xml,**/target/invoker-reports/TEST*.xml', allowEmptyResults: true
    }
  }
}

// vim: et:ts=2:sw=2:ft=groovy
