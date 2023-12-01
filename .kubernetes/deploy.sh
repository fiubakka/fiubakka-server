#!/bin/bash

NAMESPACE="game-system-1"
RELEASE_NAME="akka-backend-kafka"

if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
    echo "Creating namespace '$NAMESPACE'."
    kubectl create namespace "$NAMESPACE"
fi

if ! helm list -n "$NAMESPACE" | grep -q "$RELEASE_NAME"; then
    echo "Installing Helm release '$RELEASE_NAME' in namespace '$NAMESPACE'."
    helm install --set listeners.client.protocol=PLAINTEXT \
        "$RELEASE_NAME" oci://registry-1.docker.io/bitnamicharts/kafka \
        --namespace "$NAMESPACE"
fi

kubectl apply -f .kubernetes/akka-cluster.yaml -n "$NAMESPACE"
