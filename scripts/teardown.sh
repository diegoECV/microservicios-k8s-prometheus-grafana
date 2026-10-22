#!/usr/bin/env bash
# ==============================================================================
# Teardown: Limpieza y Eliminación de Recursos
# Valle Grande - Monitoreo Kubernetes
# ==============================================================================

set -euo pipefail

DELETE_CLUSTER=false

for arg in "$@"; do
    case $arg in
        --delete-cluster|--all)
            DELETE_CLUSTER=true
            shift
            ;;
        -h|--help)
            echo "Uso: $0 [--delete-cluster | --all]"
            echo "  Sin parámetros: Elimina deployments, services, helm release y namespaces."
            echo "  --delete-cluster: Elimina adicionalmente el clúster Minikube completo."
            exit 0
            ;;
    esac
done

echo "=========================================================="
echo "Iniciando proceso de limpieza (Teardown)..."
echo "=========================================================="

echo "1. Desinstalando Helm release de monitoreo..."
helm uninstall prometheus-stack -n monitoring 2>/dev/null || true

echo "2. Eliminando namespace del proyecto (valle-grande)..."
kubectl delete namespace valle-grande --ignore-not-found=true --timeout=60s || true

echo "3. Eliminando namespace de monitoreo (monitoring)..."
kubectl delete namespace monitoring --ignore-not-found=true --timeout=60s || true

if [ "$DELETE_CLUSTER" = true ]; then
    echo "4. Eliminando el clúster Minikube completo..."
    minikube delete
    echo "✔ Clúster Minikube eliminado."
else
    echo "ℹ Clúster Minikube preservado. Usa '--delete-cluster' para borrarlo totalmente."
fi

echo "=========================================================="
echo "✔ Teardown completado con éxito."
echo "=========================================================="
