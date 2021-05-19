def FAILING_TESTS = ""

pipeline {
   agent any
   tools {
        maven 'Maven 3.6.1'
        jdk 'JDK 9'
   }
   stages {
       stage ('Initialize') {
           steps {
               sh '''
                   echo "PATH = ${PATH}"
                   echo "M2_HOME = ${M2_HOME}"
               '''
           }
       }

       stage ('Test') {
           steps {
               script{
                   if(params.Test){
                        sh 'mvn test'
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
