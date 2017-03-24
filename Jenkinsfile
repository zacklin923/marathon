#!/usr/bin/env groovy

/**
 * Execute block and set GitHub commit status to success or failure if block
 * throws an exception.
 *
 * @param label The context for the commit status.
 * @param block The block to execute.
 */
def withCommitStatus(label, block) {
  try {
    // Execute steps in stage
    block()

    currentBuild.result = 'SUCCESS'
  } catch(error) {
    currentBuild.result = 'FAILURE'
    throw error
  } finally {

    // Mark commit with final status
    step([ $class: 'GitHubCommitStatusSetter'
         , contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: "Velocity " + label]
         ])
  }
}

def previousBuildFailed() {
    def previousResult = currentBuild.rawBuild.getPreviousBuild()?.getResult()
    return !hudson.model.Result.SUCCESS.equals(previousResult)
}

/**
 * Wrap block with a stage and a GitHub commit status setter.
 *
 * @param label The label for the stage and commit status context.
 * @param block The block to execute in stage.
 */
def stageWithCommitStatus(label, block) {
  stage(label) { withCommitStatus(label, block) }
}

node('JenkinsMarathonCI-Debian8-1-2017-02-23') { try {
        stage("Checkout Repo") {
            checkout scm
            gitCommit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
            shortCommit = gitCommit.take(8)
            currentBuild.displayName = "#${env.BUILD_NUMBER}: ${shortCommit}"
        }
        stageWithCommitStatus("1. Compile") {
          try {
            sh """if grep -q MesosDebian \$WORKSPACE/project/Dependencies.scala; then
                    MESOS_VERSION=\$(sed -n 's/^.*MesosDebian = "\\(.*\\)"/\\1/p' <\$WORKSPACE/project/Dependencies.scala)
                  else
                    MESOS_VERSION=\$(sed -n 's/^.*mesos=\\(.*\\)&&.*/\\1/p' <\$WORKSPACE/Dockerfile)
                  fi
                  sudo apt-get install -y --force-yes --no-install-recommends mesos=\$MESOS_VERSION
              """
            withEnv(['RUN_DOCKER_INTEGRATION_TESTS=true', 'RUN_MESOS_INTEGRATION_TESTS=true']) {
              sh "sudo -E sbt -Dsbt.log.format=false clean compile scapegoat doc"
            }
          } finally {
            archiveArtifacts artifacts: 'target/**/scapegoat-report/scapegoat.html', allowEmptyArchive: true
          }
        }
        stageWithCommitStatus("Test Integration") {
          try {
              timeout(time: 20, unit: 'MINUTES') {
                withEnv(['RUN_DOCKER_INTEGRATION_TESTS=true', 'RUN_MESOS_INTEGRATION_TESTS=true']) {
                   sh "sudo -E sbt -Dsbt.log.format=false clean coverage integration:test coverageReport mesos-simulation/integration:test"
                }
            }
          } finally {
            junit allowEmptyResults: true, testResults: 'target/test-reports/integration/**/*.xml'
            // scoverage does not allow the configuration of a different output
            // path: https://github.com/scoverage/sbt-scoverage/issues/211
            // The archive steps does not allow a different target path. So we
            // move the files to avoid conflicts with the reports from the unit
            // test run.
            sh "sudo mv target/scala-2.11/scoverage-report/ target/scala-2.11/scoverage-report-integration"
            sh "sudo mv target/scala-2.11/coverage-report/cobertura.xml target/scala-2.11/coverage-report/cobertura-integration.xml"
            archiveArtifacts(
                artifacts: 'target/**/coverage-report/cobertura-integration.xml, target/**/scoverage-report-integration/**',
                allowEmptyArchive: true)
          }
        }
    } catch (Exception err) {
        currentBuild.result = 'FAILURE'
        if( env.BRANCH_NAME.startsWith("releases/") || env.BRANCH_NAME == "master" ) {
          slackSend(
            message: "(;¬_¬) branch `${env.BRANCH_NAME}` failed in build `${env.BUILD_NUMBER}`. (<${env.BUILD_URL}|Open>)",
            color: "danger",
            channel: "#marathon-dev",
            tokenCredentialId: "f430eaac-958a-44cb-802a-6a943323a6a8")
        }
        throw err
    } finally {
        if( env.BRANCH_NAME.startsWith("releases/") || env.BRANCH_NAME == "master" ) {
            // Last build failed but this succeeded.
            if( previousBuildFailed() && currentBuild.result == 'SUCCESS') {
              slackSend(
                message: "╭( ･ㅂ･)و ̑̑ branch `${env.BRANCH_NAME}` is green again. (<${env.BUILD_URL}|Open>)",
                color: "good",
                channel: "#marathon-dev",
                tokenCredentialId: "f430eaac-958a-44cb-802a-6a943323a6a8")
            }
        }

        step([ $class: 'GitHubCommitStatusSetter'
             , errorHandlers: [[$class: 'ShallowAnyErrorHandler']]
             , contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: "Velocity All"]
             ])
    }
}
