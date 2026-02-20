# Deployment Guide

This guide covers deployment of the Invoice Processing Service on Docker, AWS, and Red Hat OpenShift.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Docker Deployment](#docker-deployment)
- [AWS Deployment](#aws-deployment)
- [Red Hat OpenShift Deployment](#red-hat-openshift-deployment)
- [Configuration Management](#configuration-management)
- [Monitoring and Health Checks](#monitoring-and-health-checks)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Application Dependencies

Before deploying, ensure the following external dependencies are available:

1. **PostgreSQL 16+**
   - Database: `process_db`
   - User with CREATE/INSERT/UPDATE/DELETE privileges
   - Flyway will handle schema migrations automatically

2. **Apache Kafka**
   - Minimum version: 3.0+
   - Required topics:
     - `document.received.invoice` (consumed)
     - `invoice.processed` (produced)
     - `xml.signing.requested` (produced)

3. **Netflix Eureka** (Optional for local dev)
   - Service discovery and registration
   - Default URL: `http://localhost:8761/eureka/`

4. **teda Library v1.0.0**
   - Must be installed in local Maven repository or artifact manager
   - Build command: `cd ../../../teda && mvn clean install`
   - Version: 1.0.0 (uses `Invoice_CrossIndustryInvoice` namespace)

### Build Artifacts

```bash
# Build the application
mvn clean package -DskipTests

# Verify the JAR was created
ls -lh target/invoice-processing-service-1.0.0-SNAPSHOT.jar
```

## Docker Deployment

### 1. Dockerfile

Create a multi-stage Dockerfile in the project root:

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy teda library (assumes it's been built)
COPY ../../../teda /teda
RUN cd /teda && mvn clean install -DskipTests

# Copy project files
COPY pom.xml .
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

# Add non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /build/target/invoice-processing-service-*.jar app.jar

# Create directory for logs
RUN mkdir -p /app/logs && chown -R appuser:appuser /app

USER appuser

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 2. Build Docker Image

```bash
# Build image
docker build -t invoice-processing-service:1.0.0 .

# Tag for registry
docker tag invoice-processing-service:1.0.0 your-registry/invoice-processing-service:1.0.0

# Push to registry
docker push your-registry/invoice-processing-service:1.0.0
```

### 3. Docker Compose

Create `docker-compose.yml` for local development:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: invoice-processing-postgres
    environment:
      POSTGRES_DB: process_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: invoice-processing-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 10s
      retries: 5

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    container_name: invoice-processing-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  invoice-processing-service:
    image: invoice-processing-service:1.0.0
    container_name: invoice-processing-service
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    ports:
      - "8082:8082"
    environment:
      # Database
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: process_db
      DB_USERNAME: postgres
      DB_PASSWORD: postgres

      # Kafka
      KAFKA_BROKERS: kafka:29092

      # Eureka (optional)
      EUREKA_URL: http://eureka:8761/eureka/
      EUREKA_ENABLED: "false"

      # Logging
      LOGGING_LEVEL_ROOT: INFO
      LOGGING_LEVEL_COM_WPANTHER_INVOICE_PROCESSING: DEBUG

      # JVM options
      JAVA_OPTS: "-Xms512m -Xmx1024m"
    volumes:
      - ./logs:/app/logs
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    restart: unless-stopped

volumes:
  postgres_data:
```

### 4. Run with Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f invoice-processing-service

# Check health
curl http://localhost:8082/actuator/health

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

## AWS Deployment

### Architecture Options

1. **Amazon ECS (Elastic Container Service)** - Recommended for containerized deployments
2. **Amazon EKS (Elastic Kubernetes Service)** - For Kubernetes-based deployments
3. **AWS Elastic Beanstalk** - Platform-as-a-Service option

### Option 1: Amazon ECS Deployment

#### Prerequisites

- AWS CLI configured
- Amazon RDS PostgreSQL instance
- Amazon MSK (Managed Streaming for Kafka) cluster
- ECR repository for container images

#### Step 1: Push Image to ECR

```bash
# Authenticate to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-east-1.amazonaws.com

# Create repository
aws ecr create-repository --repository-name invoice-processing-service --region us-east-1

# Tag and push image
docker tag invoice-processing-service:1.0.0 123456789012.dkr.ecr.us-east-1.amazonaws.com/invoice-processing-service:1.0.0
docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/invoice-processing-service:1.0.0
```

#### Step 2: Create Task Definition

Create `ecs-task-definition.json`:

```json
{
  "family": "invoice-processing-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::123456789012:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::123456789012:role/invoiceProcessingTaskRole",
  "containerDefinitions": [
    {
      "name": "invoice-processing-service",
      "image": "123456789012.dkr.ecr.us-east-1.amazonaws.com/invoice-processing-service:1.0.0",
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8082,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "DB_HOST",
          "value": "invoice-db.cluster-abc123.us-east-1.rds.amazonaws.com"
        },
        {
          "name": "DB_PORT",
          "value": "5432"
        },
        {
          "name": "DB_NAME",
          "value": "process_db"
        },
        {
          "name": "KAFKA_BROKERS",
          "value": "b-1.msk-cluster.abc123.kafka.us-east-1.amazonaws.com:9092,b-2.msk-cluster.abc123.kafka.us-east-1.amazonaws.com:9092"
        },
        {
          "name": "EUREKA_ENABLED",
          "value": "false"
        }
      ],
      "secrets": [
        {
          "name": "DB_USERNAME",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789012:secret:invoice/db/username"
        },
        {
          "name": "DB_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789012:secret:invoice/db/password"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/invoice-processing-service",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": [
          "CMD-SHELL",
          "curl -f http://localhost:8082/actuator/health || exit 1"
        ],
        "interval": 30,
        "timeout": 10,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```

#### Step 3: Create ECS Service

```bash
# Register task definition
aws ecs register-task-definition --cli-input-json file://ecs-task-definition.json

# Create ECS cluster
aws ecs create-cluster --cluster-name invoice-processing-cluster

# Create service
aws ecs create-service \
  --cluster invoice-processing-cluster \
  --service-name invoice-processing-service \
  --task-definition invoice-processing-service:1 \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={
    subnets=[subnet-12345678,subnet-87654321],
    securityGroups=[sg-12345678],
    assignPublicIp=DISABLED
  }" \
  --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/invoice-processing/abc123,containerName=invoice-processing-service,containerPort=8082"
```

#### Step 4: Auto Scaling Configuration

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/invoice-processing-cluster/invoice-processing-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10

# Create scaling policy (CPU-based)
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/invoice-processing-cluster/invoice-processing-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name cpu-scaling-policy \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration file://scaling-policy.json
```

`scaling-policy.json`:

```json
{
  "TargetValue": 70.0,
  "PredefinedMetricSpecification": {
    "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
  },
  "ScaleInCooldown": 300,
  "ScaleOutCooldown": 60
}
```

### Option 2: Amazon EKS Deployment

See [Red Hat OpenShift Deployment](#red-hat-openshift-deployment) section - most Kubernetes manifests are compatible with EKS.

#### EKS-Specific Setup

```bash
# Create EKS cluster
eksctl create cluster \
  --name invoice-processing-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 3 \
  --nodes-min 2 \
  --nodes-max 5 \
  --managed

# Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name invoice-processing-cluster

# Install AWS Load Balancer Controller
helm repo add eks https://aws.github.io/eks-charts
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=invoice-processing-cluster
```

### Infrastructure as Code (Terraform)

Create `main.tf` for complete AWS infrastructure:

```hcl
terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# RDS PostgreSQL
resource "aws_db_instance" "invoice_processing" {
  identifier           = "invoice-processing-db"
  engine               = "postgres"
  engine_version       = "16.1"
  instance_class       = "db.t3.medium"
  allocated_storage    = 100
  storage_encrypted    = true

  db_name  = "process_db"
  username = "postgres"
  password = var.db_password

  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.main.name

  backup_retention_period = 7
  multi_az               = true
  skip_final_snapshot    = false

  tags = {
    Name        = "invoice-processing-db"
    Environment = var.environment
  }
}

# Amazon MSK
resource "aws_msk_cluster" "invoice_kafka" {
  cluster_name           = "invoice-kafka-cluster"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type   = "kafka.m5.large"
    client_subnets  = var.private_subnet_ids
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 100
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  tags = {
    Name        = "invoice-kafka-cluster"
    Environment = var.environment
  }
}

# ECS Cluster
resource "aws_ecs_cluster" "main" {
  name = "invoice-processing-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# Application Load Balancer
resource "aws_lb" "main" {
  name               = "invoice-processing-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids

  tags = {
    Name        = "invoice-processing-alb"
    Environment = var.environment
  }
}

# Target Group
resource "aws_lb_target_group" "app" {
  name        = "invoice-processing-tg"
  port        = 8082
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 10
    unhealthy_threshold = 3
  }
}

# CloudWatch Log Group
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/invoice-processing-service"
  retention_in_days = 30
}
```

## Red Hat OpenShift Deployment

### Prerequisites

- OpenShift CLI (`oc`) installed and configured
- OpenShift cluster access with appropriate permissions
- Container image pushed to accessible registry (Quay.io, Docker Hub, or OpenShift internal registry)

### 1. Create Project/Namespace

```bash
# Login to OpenShift
oc login https://api.your-cluster.com:6443

# Create new project
oc new-project invoice-processing

# Or use existing project
oc project invoice-processing
```

### 2. Create ConfigMap

Create `configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: invoice-processing-config
  namespace: invoice-processing
data:
  application.yml: |
    spring:
      application:
        name: invoice-processing-service
      datasource:
        url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
        username: ${DB_USERNAME}
        password: ${DB_PASSWORD}
        driver-class-name: org.postgresql.Driver
        hikari:
          maximum-pool-size: 10
          minimum-idle: 2
          connection-timeout: 30000
      jpa:
        hibernate:
          ddl-auto: validate
        show-sql: false
        properties:
          hibernate:
            dialect: org.hibernate.dialect.PostgreSQLDialect
      kafka:
        bootstrap-servers: ${KAFKA_BROKERS}
        consumer:
          group-id: invoice-processing-service
          auto-offset-reset: earliest
          enable-auto-commit: false
        producer:
          acks: all
          retries: 3

    server:
      port: 8082

    management:
      endpoints:
        web:
          exposure:
            include: health,prometheus,info
      metrics:
        export:
          prometheus:
            enabled: true
```

Apply the ConfigMap:

```bash
oc apply -f configmap.yaml
```

### 3. Create Secrets

```bash
# Database credentials
oc create secret generic invoice-processing-db-secret \
  --from-literal=DB_USERNAME=postgres \
  --from-literal=DB_PASSWORD='your-secure-password'

# Or from file
cat <<EOF | oc apply -f -
apiVersion: v1
kind: Secret
metadata:
  name: invoice-processing-db-secret
  namespace: invoice-processing
type: Opaque
stringData:
  DB_USERNAME: postgres
  DB_PASSWORD: your-secure-password
EOF
```

### 4. Create Deployment

Create `deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: invoice-processing-service
  namespace: invoice-processing
  labels:
    app: invoice-processing-service
    version: v1
spec:
  replicas: 3
  selector:
    matchLabels:
      app: invoice-processing-service
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: invoice-processing-service
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8082"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: invoice-processing-sa
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
      - name: invoice-processing-service
        image: quay.io/your-org/invoice-processing-service:1.0.0
        imagePullPolicy: Always
        ports:
        - name: http
          containerPort: 8082
          protocol: TCP
        env:
        - name: DB_HOST
          value: "postgresql-service.invoice-processing.svc.cluster.local"
        - name: DB_PORT
          value: "5432"
        - name: DB_NAME
          value: "process_db"
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: invoice-processing-db-secret
              key: DB_USERNAME
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: invoice-processing-db-secret
              key: DB_PASSWORD
        - name: KAFKA_BROKERS
          value: "kafka-kafka-bootstrap.kafka.svc.cluster.local:9092"
        - name: EUREKA_ENABLED
          value: "false"
        - name: JAVA_OPTS
          value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8082
          initialDelaySeconds: 90
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8082
          initialDelaySeconds: 60
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        volumeMounts:
        - name: config
          mountPath: /app/config
          readOnly: true
      volumes:
      - name: config
        configMap:
          name: invoice-processing-config
      restartPolicy: Always
      terminationGracePeriodSeconds: 30
```

Apply the deployment:

```bash
oc apply -f deployment.yaml
```

### 5. Create Service

Create `service.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: invoice-processing-service
  namespace: invoice-processing
  labels:
    app: invoice-processing-service
spec:
  type: ClusterIP
  ports:
  - name: http
    port: 8082
    targetPort: 8082
    protocol: TCP
  selector:
    app: invoice-processing-service
```

Apply the service:

```bash
oc apply -f service.yaml
```

### 6. Create Route (OpenShift-specific)

Create `route.yaml`:

```yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: invoice-processing-service
  namespace: invoice-processing
  labels:
    app: invoice-processing-service
spec:
  to:
    kind: Service
    name: invoice-processing-service
    weight: 100
  port:
    targetPort: http
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
  wildcardPolicy: None
```

Apply the route:

```bash
oc apply -f route.yaml

# Get the route URL
oc get route invoice-processing-service -o jsonpath='{.spec.host}'
```

### 7. Horizontal Pod Autoscaler

Create `hpa.yaml`:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: invoice-processing-hpa
  namespace: invoice-processing
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: invoice-processing-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
      - type: Pods
        value: 2
        periodSeconds: 30
      selectPolicy: Max
```

Apply the HPA:

```bash
oc apply -f hpa.yaml
```

### 8. Service Account and RBAC

Create `rbac.yaml`:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: invoice-processing-sa
  namespace: invoice-processing
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: invoice-processing-role
  namespace: invoice-processing
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: invoice-processing-rolebinding
  namespace: invoice-processing
subjects:
- kind: ServiceAccount
  name: invoice-processing-sa
  namespace: invoice-processing
roleRef:
  kind: Role
  name: invoice-processing-role
  apiGroup: rbac.authorization.k8s.io
```

Apply RBAC:

```bash
oc apply -f rbac.yaml
```

### 9. Deploy PostgreSQL (Development Only)

For production, use managed database services. For development:

```bash
oc new-app postgresql-persistent \
  --name=postgresql \
  -e POSTGRESQL_USER=postgres \
  -e POSTGRESQL_PASSWORD=postgres \
  -e POSTGRESQL_DATABASE=process_db

# Wait for deployment
oc rollout status dc/postgresql
```

### 10. Deploy Kafka using Strimzi Operator

```bash
# Install Strimzi operator
oc create -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka

# Create Kafka cluster
cat <<EOF | oc apply -f -
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: kafka
  namespace: kafka
spec:
  kafka:
    version: 3.6.0
    replicas: 3
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
      - name: tls
        port: 9093
        type: internal
        tls: true
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
    storage:
      type: persistent-claim
      size: 100Gi
      deleteClaim: false
  zookeeper:
    replicas: 3
    storage:
      type: persistent-claim
      size: 10Gi
      deleteClaim: false
  entityOperator:
    topicOperator: {}
    userOperator: {}
EOF

# Create required topics
cat <<EOF | oc apply -f -
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaTopic
metadata:
  name: document.received.invoice
  namespace: kafka
  labels:
    strimzi.io/cluster: kafka
spec:
  partitions: 3
  replicas: 3
  config:
    retention.ms: 604800000
    segment.bytes: 1073741824
---
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaTopic
metadata:
  name: invoice.processed
  namespace: kafka
  labels:
    strimzi.io/cluster: kafka
spec:
  partitions: 3
  replicas: 3
---
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaTopic
metadata:
  name: xml.signing.requested
  namespace: kafka
  labels:
    strimzi.io/cluster: kafka
spec:
  partitions: 3
  replicas: 3
EOF
```

### 11. Verify Deployment

```bash
# Check pod status
oc get pods -l app=invoice-processing-service

# View logs
oc logs -f deployment/invoice-processing-service

# Check service
oc get svc invoice-processing-service

# Check route
oc get route invoice-processing-service

# Test health endpoint
ROUTE=$(oc get route invoice-processing-service -o jsonpath='{.spec.host}')
curl https://$ROUTE/actuator/health

# Check Kafka connectivity
oc exec -it deployment/invoice-processing-service -- curl localhost:8082/actuator/health
```

## Configuration Management

### Environment-Specific Configurations

#### Development

```yaml
# application-dev.yml
spring:
  kafka:
    consumer:
      auto-offset-reset: earliest
  jpa:
    show-sql: true

logging:
  level:
    com.wpanther.invoice.processing: DEBUG
```

#### Staging

```yaml
# application-staging.yml
spring:
  kafka:
    consumer:
      auto-offset-reset: latest

logging:
  level:
    com.wpanther.invoice.processing: INFO
```

#### Production

```yaml
# application-prod.yml
spring:
  kafka:
    consumer:
      auto-offset-reset: latest
    producer:
      acks: all
      retries: 5

logging:
  level:
    root: WARN
    com.wpanther.invoice.processing: INFO

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
```

### External Configuration

Use Spring Cloud Config Server or Kubernetes ConfigMaps:

```bash
# Update ConfigMap
oc create configmap invoice-processing-config \
  --from-file=application.yml \
  --dry-run=client -o yaml | oc apply -f -

# Restart pods to pick up changes
oc rollout restart deployment/invoice-processing-service
```

## Monitoring and Health Checks

### Health Endpoints

```bash
# Liveness probe
curl http://localhost:8082/actuator/health/liveness

# Readiness probe
curl http://localhost:8082/actuator/health/readiness

# Detailed health
curl http://localhost:8082/actuator/health
```

### Prometheus Metrics

```bash
# Metrics endpoint
curl http://localhost:8082/actuator/prometheus
```

### Sample Prometheus ServiceMonitor (OpenShift)

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: invoice-processing-metrics
  namespace: invoice-processing
spec:
  selector:
    matchLabels:
      app: invoice-processing-service
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
```

### Key Metrics to Monitor

1. **JVM Metrics**
   - `jvm_memory_used_bytes`
   - `jvm_gc_pause_seconds`
   - `jvm_threads_live_threads`

2. **Application Metrics**
   - `kafka_consumer_fetch_manager_records_lag`
   - `http_server_requests_seconds`
   - `process_cpu_usage`

3. **Business Metrics**
   - `invoice_processing_total`
   - `invoice_processing_duration_seconds`
   - `invoice_processing_errors_total`

### Logging

#### Centralized Logging (ELK/EFK)

```yaml
# Filebeat sidecar (Kubernetes)
- name: filebeat
  image: docker.elastic.co/beats/filebeat:8.11.0
  volumeMounts:
  - name: logs
    mountPath: /var/log/app
  - name: filebeat-config
    mountPath: /usr/share/filebeat/filebeat.yml
    subPath: filebeat.yml
```

#### CloudWatch Logs (AWS)

Configured in ECS task definition (see AWS section).

## Troubleshooting

### Common Issues

#### 1. Database Connection Failures

```bash
# Check database connectivity
oc exec -it deployment/invoice-processing-service -- bash
nc -zv postgresql-service 5432

# Verify credentials
oc get secret invoice-processing-db-secret -o yaml

# Check database logs
oc logs deployment/postgresql
```

#### 2. Kafka Connection Issues

```bash
# Test Kafka connectivity
oc exec -it deployment/invoice-processing-service -- bash
telnet kafka-kafka-bootstrap.kafka.svc.cluster.local 9092

# Check Kafka cluster status
oc get kafka -n kafka

# View Kafka logs
oc logs -n kafka deployment/kafka-kafka-0
```

#### 3. Pod Crashes or Restarts

```bash
# View recent logs
oc logs --previous deployment/invoice-processing-service

# Describe pod for events
oc describe pod <pod-name>

# Check resource usage
oc top pod <pod-name>

# Check events
oc get events --sort-by=.metadata.creationTimestamp
```

#### 4. High Memory Usage

```bash
# Generate heap dump
oc exec -it <pod-name> -- jcmd 1 GC.heap_dump /tmp/heapdump.hprof

# Copy heap dump locally
oc cp <pod-name>:/tmp/heapdump.hprof ./heapdump.hprof

# Analyze with tools like Eclipse MAT
```

#### 5. Kafka Consumer Lag

```bash
# Check consumer group lag
oc exec -it kafka-kafka-0 -n kafka -- \
  bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group invoice-processing-service

# Increase replicas if needed
oc scale deployment/invoice-processing-service --replicas=5
```

### Debugging Steps

1. **Enable Debug Logging**

```bash
# Temporary (pod restart will reset)
oc set env deployment/invoice-processing-service LOGGING_LEVEL_COM_INVOICE=DEBUG

# Permanent (update ConfigMap)
oc edit configmap invoice-processing-config
```

2. **Port Forwarding for Local Testing**

```bash
# Forward service port
oc port-forward svc/invoice-processing-service 8082:8082

# Access health endpoint
curl http://localhost:8082/actuator/health
```

3. **Interactive Shell Access**

```bash
# Access pod shell
oc rsh deployment/invoice-processing-service

# Run diagnostics
curl localhost:8082/actuator/health
env | grep -E 'DB_|KAFKA_'
```

### Performance Tuning

#### JVM Options

```yaml
env:
- name: JAVA_OPTS
  value: >-
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=200
    -XX:+UseContainerSupport
    -XX:MaxRAMPercentage=75.0
    -XX:InitialRAMPercentage=50.0
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/tmp/heapdump.hprof
    -Djava.security.egd=file:/dev/./urandom
```

#### Kafka Consumer Tuning

```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 100
      fetch-min-size: 1024
      fetch-max-wait: 500
    listener:
      concurrency: 3
      poll-timeout: 3000
```

#### Database Connection Pool

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

## Security Considerations

### 1. Container Security

```dockerfile
# Use non-root user
USER 1000

# Read-only root filesystem (add to deployment)
securityContext:
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
  capabilities:
    drop:
    - ALL
```

### 2. Network Policies (Kubernetes/OpenShift)

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: invoice-processing-netpol
  namespace: invoice-processing
spec:
  podSelector:
    matchLabels:
      app: invoice-processing-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: ingress-controller
    ports:
    - protocol: TCP
      port: 8082
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgresql
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - namespaceSelector:
        matchLabels:
          name: kafka
    ports:
    - protocol: TCP
      port: 9092
```

### 3. Secrets Management

Use external secret managers:

- **AWS**: Secrets Manager or Parameter Store
- **OpenShift**: Sealed Secrets or External Secrets Operator
- **Vault**: HashiCorp Vault integration

### 4. TLS/SSL

Enable TLS for Kafka:

```yaml
spring:
  kafka:
    security:
      protocol: SSL
    ssl:
      trust-store-location: file:/etc/kafka/secrets/kafka.truststore.jks
      trust-store-password: ${KAFKA_TRUSTSTORE_PASSWORD}
      key-store-location: file:/etc/kafka/secrets/kafka.keystore.jks
      key-store-password: ${KAFKA_KEYSTORE_PASSWORD}
```

## Backup and Disaster Recovery

### Database Backups

#### AWS RDS
- Automated backups enabled by default
- Point-in-time recovery
- Manual snapshots before major changes

#### OpenShift PostgreSQL
```bash
# Create backup
oc exec postgresql-0 -- bash -c "pg_dump -U postgres process_db > /tmp/backup.sql"

# Copy backup
oc cp postgresql-0:/tmp/backup.sql ./backup-$(date +%Y%m%d).sql
```

### Kafka Topic Backup

Use Kafka MirrorMaker 2 for cross-cluster replication or disaster recovery.

## Rolling Updates and Blue-Green Deployments

### Rolling Update (Default)

```bash
# Update image
oc set image deployment/invoice-processing-service \
  invoice-processing-service=quay.io/your-org/invoice-processing-service:1.1.0

# Monitor rollout
oc rollout status deployment/invoice-processing-service

# Rollback if needed
oc rollout undo deployment/invoice-processing-service
```

### Blue-Green Deployment

```bash
# Deploy green version
oc apply -f deployment-green.yaml

# Verify green deployment
oc get pods -l version=green

# Switch route to green
oc patch route invoice-processing-service -p '{"spec":{"to":{"name":"invoice-processing-service-green"}}}'

# Delete blue deployment after verification
oc delete deployment invoice-processing-service-blue
```

## References

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [OpenShift Documentation](https://docs.openshift.com/)
- [AWS ECS Documentation](https://docs.aws.amazon.com/ecs/)
- [Strimzi Kafka Operator](https://strimzi.io/)
- [Prometheus Monitoring](https://prometheus.io/docs/)
