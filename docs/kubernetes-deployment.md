# Deployment to Kubernetes

Chief of State leverages the [Akka Kubernetes Integration](https://doc.akka.io/docs/akka-management/current/kubernetes-deployment/index.html) for cluster bootstrap and node discovery. See below for common configuration.

### Environment Variables

Ensure the following env vars are set:

| environment variable | description |
| --- | --- |
| COS_DEPLOYMENT_MODE | set to "kubernetes" to instruct COS to leverage the k8s API |
| POD_IP | IP of the pod running chief of state (see note below) |
| COS_KUBERNETES_APP_LABEL | Set to the app label of the k8s pod, which Akka will use to discover all sibling nodes in the cluster. |
| COS_KUBERNETES_REPLICA_COUNT | must match the replica count on your deployment. Defaults to "1" |


`POD_IP` environment variable can be dynamically set with the following container environment instruction:
```yaml
env:
  - name: POD_IP
    valueFrom:
      fieldRef:
        apiVersion: v1
        fieldPath: status.podIP
```

### Service Account and Role

Akka leverages the K8s API to discover sibling nodes in your COS cluster.

Your COS pod requires the following permisions for pods:
- get
- watch
- list

This can be accomplished with the following K8s Service Account, Role, and RoleBinding:
```yaml
# create the cluster role that can read pods
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: pod-reader
  namespace: default
rules:
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "watch", "list"]

---

# create a service account for chief of state
apiVersion: v1
kind: ServiceAccount
metadata:
  name: chief-of-state-sa
  namespace: default

---

# bind your pod reader role to your service account
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: RoleBinding
metadata:
  name: chief-of-state-sa-pod-reader
  namespace: default
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
subjects:
  - kind: ServiceAccount
    name: chief-of-state-sa
    namespace: default
```

... then assign the service account to your deployment like so ...
```yaml
apiVersion: "apps/v1"
kind: Deployment
metadata:
  name: my-app-chief-of-state
  namespace: default
  labels:
    app: my-app-chief-of-state
spec:
  selector:
    matchLabels:
      app: my-app-chief-of-state
  template:
    metadata:
      labels:
        app: my-app-chief-of-state
    spec:
      # set the service account for this pod
      serviceAccountName: chief-of-state-sa
```
