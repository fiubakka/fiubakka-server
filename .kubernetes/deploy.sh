#!/bin/bash

NAMESPACE="fiubakka-server-1"
KAFKA_RELEASE_NAME="fiubakka-kafka"
POSTGRES_RELEASE_NAME="fiubakka-postgres"

if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
    echo "Creating namespace '$NAMESPACE'."
    kubectl create namespace "$NAMESPACE"
fi

if ! helm list -n "$NAMESPACE" | grep -q "$KAFKA_RELEASE_NAME"; then
    echo "Installing Helm release '$KAFKA_RELEASE_NAME' in namespace '$NAMESPACE'."
    helm install \
        --set listeners.client.protocol=PLAINTEXT \
        --set extraConfig="| 
            log.retention.minutes=2
            log.retention.check.interval.ms=30000" \
        "$KAFKA_RELEASE_NAME" oci://registry-1.docker.io/bitnamicharts/kafka \
        --namespace "$NAMESPACE"
fi

if ! helm list -n "$NAMESPACE" | grep -q "$POSTGRES_RELEASE_NAME"; then
    echo "Installing Helm release '$POSTGRES_RELEASE_NAME' in namespace '$NAMESPACE'."
    helm install \
        "$POSTGRES_RELEASE_NAME" oci://registry-1.docker.io/bitnamicharts/postgresql \
        --set auth.enablePostgresUser=true \
        --set auth.postgresPassword=postgres \
        --namespace "$NAMESPACE"
fi

echo "Waiting for Postgres deployment to be ready..."
kubectl wait --for=condition=ready pod/fiubakka-postgres-postgresql-0 --timeout=60s

kubectl apply -f .kubernetes/db-setup.yaml -n "$NAMESPACE"

kubectl apply -f .kubernetes/akka-cluster.yaml -n "$NAMESPACE"
