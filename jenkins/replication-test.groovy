pipeline {
  agent any
  parameters {
    string(name: 'GIT_REPO', defaultValue: 'https://github.com/percona/percona-server.git', description: 'PS repo for build.')
    string(name: 'BRANCH', defaultValue: '8.0', description: 'Target branch')
    string(name: 'PT_BIN', defaultValue: 'https://www.percona.com/downloads/percona-toolkit/3.1.0/binary/tarball/percona-toolkit-3.1.0_x86_64.tar.gz', description: 'PT binary tarball')
    string(name: 'PXB_BIN', defaultValue: 'https://www.percona.com/downloads/Percona-XtraBackup-LATEST/Percona-XtraBackup-8.0-7/binary/tarball/percona-xtrabackup-8.0.7-Linux-x86_64.libgcrypt20.tar.gz', description: 'PXB binary tarball')
  }
  environment {
    DOCKER_OS = "perconalab/ps-build:ubuntu-bionic"
  }
  stages {
    stage('Build PS binary') {
      steps {
        script {
          def setupResult = build job: 'test-tomislav', parameters: [
            string(name: 'GIT_REPO', value: "${GIT_REPO}"),
            string(name: 'BRANCH', value: "${BRANCH}"),
            string(name: 'DOCKER_OS', value: "${DOCKER_OS}"),
            string(name: 'CMAKE_BUILD_TYPE', value: "Debug"),
            string(name: 'WITH_TOKUDB', value: "ON"),
            string(name: 'WITH_ROCKSDB', value: "ON"),
            string(name: 'WITH_ROUTER', value: "ON"),
            string(name: 'WITH_MYSQLX', value: "ON"),
            string(name: 'DEFAULT_TESTING', value: "no"),
            string(name: 'HOTBACKUP_TESTING', value: "no"),
            string(name: 'TOKUDB_ENGINES_MTR', value: "no")
          ], propagate: false, wait: true
          // Navigate to jenkins > Manage jenkins > In-process Script Approval
          // staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods putAt java.lang.Object java.lang.String java.lang.Object
          env['PIPELINE_BUILD_NUMBER'] = setupResult.getNumber()
        }
        sh '''
          echo "${PIPELINE_BUILD_NUMBER}" > PIPELINE_BUILD_NUMBER
        '''
        archiveArtifacts artifacts: 'PIPELINE_BUILD_NUMBER', fingerprint: true
        
        copyArtifacts filter: 'public_url', fingerprintArtifacts: true, projectName: 'test-tomislav', selector: specific("${PIPELINE_BUILD_NUMBER}")
        script {
          env['PS_BIN'] = sh(script: 'cat public_url|grep binary|grep -o "https://.*"', returnStdout: true)
        }
      }
    }
    stage('Run tests') {
      parallel {
        stage('Test InnoDB') {
          agent {
            docker {
              image "${DOCKER_OS}"
              label "docker-32gb"
            }
          }
          steps {
            sh '''
              # prepare
              wget https://repo.percona.com/apt/percona-release_latest.generic_all.deb
              sudo dpkg -i percona-release_latest.generic_all.deb
              sudo percona-release enable original
              sudo apt update
              sudo apt install sysbench
              #
              TEST_DIR="repl-test"
              rm -rf percona-qa
              rm -rf ${TEST_DIR}
              rm -f *.tar.gz
              CUR_PWD=${PWD}
              mkdir -p ${TEST_DIR}
              cd ${TEST_DIR}
              wget -q ${PS_BIN}
              wget -q ${PT_BIN}
              wget -q ${PXB_BIN}
              PS_TARBALL="$(tar -ztf binary.tar.gz|head -n1|sed 's:/$::').tar.gz"
              mv binary.tar.gz ${PS_TARBALL}
              cd -
              git clone https://github.com/Percona-QA/percona-qa.git --depth 1
              ${CUR_PWD}/percona-qa/ps-async-repl-test.sh --workdir=${CUR_PWD}/${TEST_DIR} --build-number=${BUILD_NUMBER}
              tar czf logs-repl-innodb-${BUILD_NUMBER}.tar.gz ${CUR_PWD}/${TEST_DIR}/${BUILD_NUMBER}/logs
            '''
            archiveArtifacts artifacts: "logs-repl-innodb-${BUILD_NUMBER}.tar.gz", fingerprint: true
          }
        } //End stage Test InnoDB
        stage('Test RocksDB') {
          agent {
            docker {
              image "${DOCKER_OS}"
              label "docker-32gb"
            }
          }
          steps {
            sh '''
              echo "${PIPELINE_BUILD_NUMBER}"
            '''
          }
        } //End stage Test RocksDB
        stage('Test TokuDB') {
          agent {
            docker {
              image "${DOCKER_OS}"
              label "docker-32gb"
            }
          }
          steps {
            sh '''
              echo "${PIPELINE_BUILD_NUMBER}"
            '''
          }
        } //End stage Test TokuDB
        stage('Test encryption with keyring file') {
          agent {
            docker {
              image "${DOCKER_OS}"
              label "docker-32gb"
            }
          }
          steps {
            sh '''
              echo "${PIPELINE_BUILD_NUMBER}"
            '''
          }
        } //End stage Test encryption with keyring file
        stage('Test encryption with keyring vault') {
          agent {
            docker {
              image "${DOCKER_OS}"
              label "docker-32gb"
            }
          }
          steps {
            sh '''
              echo "${PIPELINE_BUILD_NUMBER}"
            '''
          }
        } //End stage Test encryption with keyring vault
      } //End parallel
    } //End stage Run tests
  } //End stages
} //End pipeline
