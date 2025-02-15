// Licensed to Elasticsearch B.V. under one or more contributor
// license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright
// ownership. Elasticsearch B.V. licenses this file to you under
// the Apache License, Version 2.0 (the "License"); you may
// not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

pipeline {
  agent { label 'master' }
  environment {
    REPO = 'apm-pipeline-library'
    BASE_DIR = "src/github.com/elastic/${env.REPO}"
    NOTIFY_TO = credentials('notify-to')
    PIPELINE_LOG_LEVEL = 'DEBUG'
    LANG = "C.UTF-8"
    LC_ALL = "C.UTF-8"
    SLACK_CHANNEL = '#observablt-bots'
    GITHUB_CHECK = 'true'
    BRANCH_NAME = "${params.branch_specifier}"
  }
  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '5'))
    timestamps()
    ansiColor('xterm')
    disableResume()
    durabilityHint('PERFORMANCE_OPTIMIZED')
  }
  parameters {
    string(name: 'branch_specifier', defaultValue: 'main', description: 'the Git branch specifier to scan.')
  }
  stages {
    stage('Checkout jobs'){
      steps {
        deleteDir()
        gitCheckout(basedir: "${BASE_DIR}")
      }
    }
    stage('Unit test'){
      when {
        expression {
          return false
        }
      }
      steps {
        dir("${BASE_DIR}/.ci/jobDSL"){
          sh(label: 'Run tests', script: './gradlew clean test --stacktrace')
        }
      }
      post {
        always {
          junit(allowEmptyResults: true,
            testDataPublishers: [
              [$class: 'AttachmentPublisher']
            ],
            testResults:"${BASE_DIR}/.ci/jobDSL/build/test-results/test/TEST-*.xml"
          )
        }
      }
    }
    stage('Generate Jobs') {
      steps {
        jobDsl(
          ignoreExisting: true,
          failOnMissingPlugin: true,
          failOnSeedCollision: true,
          sandbox: true,
          targets: "${BASE_DIR}/.ci/jobDSL/jobs/fleet-ci/folder.groovy",
          unstableOnDeprecation: true
        )
        jobDsl(
          ignoreExisting: true,
          failOnMissingPlugin: true,
          failOnSeedCollision: true,
          removedConfigFilesAction: 'DELETE',
          removedJobAction: 'DELETE',
          removedViewAction: 'DELETE',
          sandbox: true,
          targets: "${BASE_DIR}/.ci/jobDSL/jobs/fleet-ci/**/*.groovy",
          unstableOnDeprecation: true
        )
      }
    }
  }
}
