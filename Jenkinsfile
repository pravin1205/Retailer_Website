// ─────────────────────────────────────────────────────────────────────────────
// Marketly — Jenkins Declarative Pipeline
//
// What this pipeline does:
//   1. Checkout source code from GitHub
//   2. Run backend tests (Maven)
//   3. Build all backend microservices into fat JARs (Maven, parallel)
//   4. Build the frontend (Node.js / Vite / TanStack Start)
//   5. Build Docker images for every service (parallel)
//   6. Push images to Docker Hub
//   7. Deploy the full stack via docker compose on THIS machine (local deploy)
//
// Required Jenkins credentials (Manage Jenkins → Credentials):
//   docker-registry-credentials  → Username+Password  (Docker Hub login)
//   marketly-jwt-secret           → Secret text        (JWT signing secret)
//
// Required Jenkins plugins:
//   Pipeline, Git, Docker Pipeline, Credentials Binding, NodeJS, JUnit
//
// Required Jenkins Global Tool Config (Manage Jenkins → Tools):
//   Maven  named "Maven-3.9"   version 3.9.x
//   JDK    named "JDK-21"      version 21
//   NodeJS named "Node-22"     version 22
// ─────────────────────────────────────────────────────────────────────────────

pipeline {

    agent any

    // ── Tool versions ─────────────────────────────────────────────────────────
    // Names must match exactly what you set in Jenkins → Global Tool Config.
    tools {
        maven  'Maven-3.9'
        jdk    'JDK-21'
        nodejs 'Node-22'
    }

    // ── Pipeline-level env vars ───────────────────────────────────────────────
    environment {
        // Docker Hub registry prefix — images are tagged as:
        //   docker.io/pravinjosh/identity-service:abc1234
        //   docker.io/pravinjosh/identity-service:latest
        REGISTRY  = 'pravinjosh'

        // Image tag: short Git commit hash so every build is traceable.
        // Falls back to 'latest' if GIT_COMMIT is not set.
        IMAGE_TAG = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"

        // Absolute path to the backend/ folder on this machine.
        // docker compose is run from here.
        DEPLOY_PATH = 'd:\\Retailer_Website\\backend'

        // JWT secret injected from Jenkins credentials store.
        // Referenced in docker-compose.yml as ${JWT_SECRET}.
        JWT_SECRET = credentials('marketly-jwt-secret')
    }

    // ── Options ───────────────────────────────────────────────────────────────
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
        timestamps()
    }

    stages {

        // ── Stage 1: Checkout ─────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                echo "Building commit: ${env.GIT_COMMIT}"
                echo "Branch: ${env.GIT_BRANCH}"
            }
        }

        // ── Stage 2: Backend Tests ────────────────────────────────────────────
        stage('Backend: Test') {
            steps {
                dir('backend') {
                    bat '''
                        mvn test ^
                            --no-transfer-progress ^
                            -Dspring.profiles.active=test
                    '''
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'backend/**/target/surefire-reports/*.xml'
                }
            }
        }

        // ── Stage 3: Backend Build JARs ───────────────────────────────────────
        // Build all modules into fat jars. Tests already ran in Stage 2.
        stage('Backend: Build JARs') {
            steps {
                dir('backend') {
                    bat '''
                        mvn package ^
                            -DskipTests ^
                            --no-transfer-progress
                    '''
                }
            }
        }

        // ── Stage 4: Frontend Build ───────────────────────────────────────────
        stage('Frontend: Build') {
            steps {
                bat 'npm ci --ignore-scripts'
                bat 'npm run build'
            }
        }

        // ── Stage 5: Docker Build (parallel) ─────────────────────────────────
        // All 9 images build in parallel to save time.
        // Backend Dockerfile accepts SERVICE_NAME as a build arg.
        // Frontend Dockerfile is at the project root.
        stage('Docker: Build Images') {
            parallel {

                stage('image: identity-service') {
                    steps {
                        dir('backend') {
                            bat """
                                docker build ^
                                    --build-arg SERVICE_NAME=identity-service ^
                                    -t %REGISTRY%/identity-service:%IMAGE_TAG% ^
                                    -t %REGISTRY%/identity-service:latest ^
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: tenant-service') {
                    steps {
                        dir('backend') {
                            bat """
                                docker build ^
                                    --build-arg SERVICE_NAME=tenant-service ^
                                    -t %REGISTRY%/tenant-service:%IMAGE_TAG% ^
                                    -t %REGISTRY%/tenant-service:latest ^
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: product-service') {
                    steps {
                        dir('backend') {
                            bat """
                                docker build ^
                                    --build-arg SERVICE_NAME=product-service ^
                                    -t %REGISTRY%/product-service:%IMAGE_TAG% ^
                                    -t %REGISTRY%/product-service:latest ^
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: customer-service') {
                    steps {
                        dir('backend') {
                            bat """
                                docker build ^
                                    --build-arg SERVICE_NAME=customer-service ^
                                    -t %REGISTRY%/customer-service:%IMAGE_TAG% ^
                                    -t %REGISTRY%/customer-service:latest ^
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: order-service') {
                    steps {
                        dir('backend') {
                            bat """
                                docker build ^
                                    --build-arg SERVICE_NAME=order-service ^
                                    -t %REGISTRY%/order-service:%IMAGE_TAG% ^
                                    -t %REGISTRY%/order-service:latest ^
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: notification-service') {
                    steps {
                        dir('backend') {
                            bat """
                                docker build ^
                                    --build-arg SERVICE_NAME=notification-service ^
                                    -t %REGISTRY%/notification-service:%IMAGE_TAG% ^
                                    -t %REGISTRY%/notification-service:latest ^
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: analytics-service') {
                    steps {
                        dir('backend') {
                            bat """
                                docker build ^
                                    --build-arg SERVICE_NAME=analytics-service ^
                                    -t %REGISTRY%/analytics-service:%IMAGE_TAG% ^
                                    -t %REGISTRY%/analytics-service:latest ^
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: api-gateway') {
                    steps {
                        dir('backend') {
                            bat """
                                docker build ^
                                    --build-arg SERVICE_NAME=api-gateway ^
                                    -t %REGISTRY%/api-gateway:%IMAGE_TAG% ^
                                    -t %REGISTRY%/api-gateway:latest ^
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: frontend') {
                    steps {
                        bat """
                            docker build ^
                                -t %REGISTRY%/frontend:%IMAGE_TAG% ^
                                -t %REGISTRY%/frontend:latest ^
                                -f Dockerfile .
                        """
                    }
                }

            } // end parallel
        }

        // ── Stage 6: Docker Push ──────────────────────────────────────────────
        // Log in to Docker Hub, push all 9 images (commit tag + latest), log out.
        stage('Docker: Push Images') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'docker-registry-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    bat 'echo %DOCKER_PASS% | docker login -u %DOCKER_USER% --password-stdin'

                    script {
                        def services = [
                            'identity-service',
                            'tenant-service',
                            'product-service',
                            'customer-service',
                            'order-service',
                            'notification-service',
                            'analytics-service',
                            'api-gateway',
                            'frontend'
                        ]
                        services.each { svc ->
                            bat "docker push %REGISTRY%/${svc}:%IMAGE_TAG%"
                            bat "docker push %REGISTRY%/${svc}:latest"
                        }
                    }

                    bat 'docker logout'
                }
            }
        }

        // ── Stage 7: Deploy (local) ───────────────────────────────────────────
        // Jenkins and Docker are on the same Windows machine, so we call
        // docker compose directly — no SSH needed.
        //
        // This stage:
        //   1. Pulls the images we just pushed from Docker Hub
        //   2. Recreates only the microservice + frontend containers
        //      (leaves postgres, redis, kafka, zookeeper running)
        //   3. Prunes dangling images to free disk
        stage('Deploy: Local docker compose') {
            steps {
                withCredentials([
                    string(credentialsId: 'marketly-jwt-secret', variable: 'JWT_SECRET_VAL')
                ]) {
                    bat """
                        cd /d %DEPLOY_PATH%

                        REM Pull freshly pushed images from Docker Hub
                        set JWT_SECRET=%JWT_SECRET_VAL%
                        docker compose pull ^
                            identity-service ^
                            tenant-service ^
                            product-service ^
                            customer-service ^
                            order-service ^
                            notification-service ^
                            analytics-service ^
                            api-gateway ^
                            frontend

                        REM Start / recreate microservice containers.
                        REM Infrastructure (postgres, redis, kafka, zookeeper) starts
                        REM automatically via depends_on if not already running.
                        docker compose up -d ^
                            --remove-orphans ^
                            identity-service ^
                            tenant-service ^
                            product-service ^
                            customer-service ^
                            order-service ^
                            notification-service ^
                            analytics-service ^
                            api-gateway ^
                            frontend

                        REM Free disk — remove images with no tag (old builds)
                        docker image prune -f

                        echo Deployment complete.
                    """
                }
            }
        }

    } // end stages

    // ── Post-build actions ────────────────────────────────────────────────────
    post {
        success {
            echo "Build ${env.IMAGE_TAG} deployed successfully."
            echo "Frontend:   http://localhost:3000"
            echo "API Gateway: http://localhost:8081"
            echo "Kafka UI:   http://localhost:8090"
            echo "Grafana:    http://localhost:3001"
        }
        failure {
            echo "Build failed. Check the console output above for errors."
        }
        always {
            // Clean the Jenkins workspace after each build to free disk.
            cleanWs()
        }
    }
}
