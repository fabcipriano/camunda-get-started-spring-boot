pipeline {
    agent {

        docker {
            image 'maven:3.9.2-eclipse-temurin-17'
         }
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

        stage ('Build') {
            steps {
                echo 'This is a minimal pipeline.'
                sh 'mvn clean install'
            }
        }
    }
}