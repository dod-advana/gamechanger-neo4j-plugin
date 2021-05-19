def FAILING_TESTS = ""

pipeline {
   agent any
   environment {
        JAVA_HOME = '/var/lib/jenkins/tools/hudson.model.JDK/JDK_11/bin/java'
   }
   tools {
        maven 'Maven 3.6.1'
        jdk 'JDK 11'
   }
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
                        sh 'JAVA_HOME=/var/lib/jenkins/tools/hudson.model.JDK/JDK_11/bin/java mvn test'
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
