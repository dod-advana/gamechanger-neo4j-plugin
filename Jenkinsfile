def FAILING_TESTS = ""

pipeline {
   agent any
   stages {
       stage ('Initialize') {
           steps {
               sh '''
                   echo "JAVA_HOME = ${JAVA_HOME}"
                   echo "PATH = ${PATH}"
                   echo "M2_HOME = ${M2_HOME}"
               '''
           }
       }

       stage ('Test') {
           steps {
               script{
                   if(params.Test){
                         docker.withRegistry('http://092912502985.dkr.ecr.us-east-1.amazonaws.com') {
                            docker.image('maven:3.8.1-jdk-11').inside {
                                sh 'mvn test'
                            }
                        }
                   } else {
                        echo 'Skipping GAMECHANGER Neo4j Plugin Unit Tests'
                   }
               }
           }
           post {
                success {
                    script {
                        if(params.Notify && Params.Test) {
                            slackSend(color: '#00FF00', message: "Gamechanger Neo4j Plugin Test *SUCCESS*.", channel: 'gamechanger-jenkins')
                        }
                    }
                }
                failure {
                    script {
                        if (params.Notify && Params.Test) {
                            slackSend(color: '#FF9FA1', message: "Gamechanger Neo4j Plugin Test *FAILURE*.", channel: 'gamechanger-jenkins')
                        }
                    }
                }
            }
       }
   }
}
