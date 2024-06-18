#!/bin/bash

NAMESPACE="fiubakka-server-1"
KAFKA_RELEASE_NAME="fiubakka-kafka"
POSTGRES_RELEASE_NAME="fiubakka-postgres"
PROMETHEUS_RELEASE_NAME="fiubakka-prometheus"
GRAFANA_RELEASE_NAME="fiubakka-grafana"

if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
    echo "Creating namespace '$NAMESPACE'."
    kubectl create namespace "$NAMESPACE"
fi

kubectl apply -f .kubernetes/grafana-cinnamon.yaml -n "$NAMESPACE"

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

if ! helm list -n "$NAMESPACE" | grep -q "$PROMETHEUS_RELEASE_NAME"; then
    echo "Installing Helm release '$PROMETHEUS_RELEASE_NAME' in namespace '$NAMESPACE'."
    helm install \
        "$PROMETHEUS_RELEASE_NAME" prometheus-community/prometheus \
        --namespace "$NAMESPACE"
fi

if ! helm list -n "$NAMESPACE" | grep -q "$GRAFANA_RELEASE_NAME"; then
    echo "Installing Helm release '$GRAFANA_RELEASE_NAME' in namespace '$NAMESPACE'."
    helm install \
        "$GRAFANA_RELEASE_NAME" grafana/grafana \
        -f .kubernetes/helm/grafana.yaml \
        --namespace "$NAMESPACE"
fi

echo "Waiting for Kafka deployment to be ready..."
for i in {0..2}; do
    kubectl wait --for=condition=ready pod/fiubakka-kafka-controller-"$i" -n "$NAMESPACE" --timeout=120s
done

kubectl run fiubakka-kafka-client --restart='Never' --image docker.io/bitnami/kafka:3.7.0-debian-12-r0 \
    --namespace "$NAMESPACE" --command -- \
    kafka-topics.sh --bootstrap-server fiubakka-kafka.fiubakka-server-1.svc.cluster.local:9092 --create --topic game-zone --partitions 4

echo "Waiting for Postgres deployment to be ready..."
kubectl wait --for=condition=ready pod/fiubakka-postgres-postgresql-0 -n "$NAMESPACE" --timeout=120s

kubectl apply -f .kubernetes/db-setup.yaml -n "$NAMESPACE"

kubectl apply -f .kubernetes/akka-cluster.yaml -n "$NAMESPACE"
