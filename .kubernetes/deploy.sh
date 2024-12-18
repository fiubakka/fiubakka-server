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

if ! kubectl get pvc grafana-plugins-pvc -n "$NAMESPACE" &> /dev/null; then
    echo "Creating PVC 'grafana-plugins' in namespace '$NAMESPACE'."
    kubectl apply -f .kubernetes/grafana-cinnamon.yaml -n "$NAMESPACE"
    kubectl wait --for=condition=ready pod/grafana-plugins-tmp -n "$NAMESPACE" --timeout=60s
    kubectl cp telemetry/grafana/plugins/cinnamon-prometheus-app grafana-plugins-tmp:/var/lib/grafana/plugins/ -n "$NAMESPACE"
    kubectl delete pod grafana-plugins-tmp -n "$NAMESPACE"
fi

if ! helm list -n "$NAMESPACE" | grep -q "$KAFKA_RELEASE_NAME"; then
    echo "Installing Helm release '$KAFKA_RELEASE_NAME' in namespace '$NAMESPACE'."
    helm install \
        -f .kubernetes/helm/kafka.yaml \
        "$KAFKA_RELEASE_NAME" oci://registry-1.docker.io/bitnamicharts/kafka \
        --namespace "$NAMESPACE"
fi

if ! helm list -n "$NAMESPACE" | grep -q "$POSTGRES_RELEASE_NAME"; then
    echo "Installing Helm release '$POSTGRES_RELEASE_NAME' in namespace '$NAMESPACE'."
    helm install \
        "$POSTGRES_RELEASE_NAME" oci://registry-1.docker.io/bitnamicharts/postgresql \
        -f .kubernetes/helm/postgres.yaml \
        --namespace "$NAMESPACE"
fi

if ! helm list -n "$NAMESPACE" | grep -q "$PROMETHEUS_RELEASE_NAME"; then
    echo "Installing Helm release '$PROMETHEUS_RELEASE_NAME' in namespace '$NAMESPACE'."
    helm install \
        "$PROMETHEUS_RELEASE_NAME" prometheus-community/prometheus \
        -f .kubernetes/helm/prometheus.yaml \
        --namespace "$NAMESPACE"
fi

if ! helm list -n "$NAMESPACE" | grep -q "$GRAFANA_RELEASE_NAME"; then
    echo "Installing Helm release '$GRAFANA_RELEASE_NAME' in namespace '$NAMESPACE'."
    helm install \
        "$GRAFANA_RELEASE_NAME" grafana/grafana \
        -f .kubernetes/helm/grafana.yaml \
        --namespace "$NAMESPACE"
fi

echo "Waiting for Postgres deployment to be ready..."
kubectl wait --for=condition=ready pod/fiubakka-postgres-postgresql-0 -n "$NAMESPACE" --timeout=120s

kubectl apply -f .kubernetes/db-setup.yaml -n "$NAMESPACE"

kubectl apply -f .kubernetes/akka-cluster.yaml -n "$NAMESPACE"
